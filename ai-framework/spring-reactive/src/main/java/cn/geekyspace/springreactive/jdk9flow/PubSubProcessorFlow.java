package cn.geekyspace.springreactive.jdk9flow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

/**
 * JDK 9 Flow：发布者 → 处理器 → 订阅者。
 * 处理器接收上游消息，转为大写后发布给下游。
 */
@Slf4j
public class PubSubProcessorFlow {

    // 处理器：既是上游的订阅者，也是下游的发布者
    static class DataTransformProcessor extends SubmissionPublisher<String>
            implements Flow.Processor<String, String> {

        // 用于向上游请求数据
        private Flow.Subscription upstreamSubscription;

        public DataTransformProcessor(Executor executor, int maxBufferCapacity) {
            super(executor, maxBufferCapacity); // 设置下游发布使用的线程池和缓冲区
        }

        /** 上游订阅建立时调用 */
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.upstreamSubscription = subscription;
            // 请求第一条数据
            this.upstreamSubscription.request(1);
        }

        /** 收到上游数据时调用 */
        @Override
        public void onNext(String item) {
            // 转为大写，发布给下游
            String processedItem = "【已加工】" + item.toUpperCase(Locale.ROOT);
            this.submit(processedItem); // 下游缓冲区满时阻塞

            // 提交后再请求下一条（不等待下游消费完成）
            this.upstreamSubscription.request(1);
        }

        /** 上游发生异常时调用 */
        @Override
        public void onError(Throwable throwable) {
            this.closeExceptionally(throwable); // 向当前订阅者发出错误信号
        }

        /** 上游正常结束时调用 */
        @Override
        public void onComplete() {
            this.close(); // 剩余消息消费完后通知下游完成
        }
    }

    // 订阅者：接收并处理加工后的消息
    @Slf4j
    @RequiredArgsConstructor
    static class MessageSubscriber implements Flow.Subscriber<String> {
        // 用于请求数据或取消订阅
        private Flow.Subscription subscription;

        private final String name;
        // 通知主线程订阅结束，适合本示例的一次性等待
        // 需要传递结果或异常时，优先使用 CompletableFuture
        private final CountDownLatch latch;

        /** 订阅建立时调用 */
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            log.info("[{}] 成功连接到处理器", name);
            // 请求第一条数据
            this.subscription.request(1);
        }

        /** 收到数据时调用 */
        @Override
        public void onNext(String item) {
            log.info("[{}] 消费端收到加工数据: {}", name, item);
            try {
                // 模拟耗时处理
                TimeUnit.MILLISECONDS.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // 处理完再请求下一条（背压）
            this.subscription.request(1);
        }

        /** 发生异常时调用 */
        @Override
        public void onError(Throwable throwable) {
            log.error("[{}] 订阅链条异常终止: ", name, throwable);
            latch.countDown();
        }

        /** 数据全部消费完、流正常结束时调用 */
        @Override
        public void onComplete() {
            log.info("[{}] 所有加工数据消费完毕，流已优雅关闭。", name);
            latch.countDown();
        }
    }

    /** 发布消息，经处理器加工后等待消费结束 */
    public static void main(String[] args) throws InterruptedException {
        // 专用线程池：执行处理器和订阅者回调
        ExecutorService flowExecutor = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                Thread.ofPlatform().name("flow-processor-worker-", 1).factory()
        );

        CountDownLatch latch = new CountDownLatch(2); // 等待两个最终订阅者结束
        int bufferCapacity = 8; // 每个订阅者的缓冲区容量

        try (DataTransformProcessor processor = new DataTransformProcessor(flowExecutor, bufferCapacity)) {
            try (SubmissionPublisher<String> publisher = new SubmissionPublisher<>(flowExecutor, bufferCapacity)) {
                MessageSubscriber subscriberA = new MessageSubscriber("Subscriber-A", latch);
                MessageSubscriber subscriberB = new MessageSubscriber("Subscriber-B", latch);

                // 先接下游，再接上游，避免处理器产出结果时尚无订阅者
                processor.subscribe(subscriberA); // A 订阅处理器：处理器 → A（加工数据）
                processor.subscribe(subscriberB); // B 订阅处理器：处理器 → B（加工数据）
                publisher.subscribe(processor);   // 处理器订阅发布者：发布者 → 处理器（原始数据）

                // 先绑定整条订阅链，再发布消息
                List<String> items = List.of("apple", "banana", "orange");
                log.info("====== 生产端开始异步发布数据 ======");
                for (String item : items) {
                    log.info("[Publisher] 投入原始数据: {}", item);
                    publisher.submit(item); // 提交消息，缓冲区满时阻塞
                }
            } // 关闭源发布者，由处理器向下游传递完成信号

            latch.await(); // 等待最终订阅者结束，再释放处理器
        } finally {
            flowExecutor.shutdown(); // 正常结束或抛出异常时都关闭线程池
        }
        log.info("====== 加工发布订阅程序安全退出 ======");
    }
}
