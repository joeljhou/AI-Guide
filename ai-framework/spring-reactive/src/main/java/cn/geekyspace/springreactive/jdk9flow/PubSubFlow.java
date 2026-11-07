package cn.geekyspace.springreactive.jdk9flow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.concurrent.*;

/**
 * JDK 9 Flow：异步发布消息，按需消费。
 */
@Slf4j
public class PubSubFlow {

    // 订阅者：接收并处理消息
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
            log.info("[{}] 成功建立订阅通道", name);
            // 请求第一条数据
            this.subscription.request(1);
        }

        /** 收到数据时调用 */
        @Override
        public void onNext(String item) {
            log.info("[{}] 消费端收到数据: {}", name, item);
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
            latch.countDown();  // 计数器值 -1
        }

        /** 数据全部消费完、流正常结束时调用 */
        @Override
        public void onComplete() {
            log.info("[{}] 所有数据异步消费完毕，流已优雅关闭。", name);
            latch.countDown();  // 计数器值 -1
        }
    }

    /** 发布消息并等待消费结束 */
    public static void main(String[] args) throws InterruptedException {
        // 专用线程池：执行订阅者回调
        ExecutorService flowExecutor = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,    // 核心线程数2，最大线程数4，空闲线程60秒后回收
                new LinkedBlockingQueue<>(1000),                               // 阻塞队列容量1000
                Thread.ofPlatform().name("flow-worker-", 1).factory()      // 线程名自动递增
        );

        CountDownLatch latch = new CountDownLatch(2);  // 等待两个订阅者结束
        int bufferCapacity = 8; // 每个订阅者的缓冲区容量

        // 创建异步发布者
        try (SubmissionPublisher<String> publisher = new SubmissionPublisher<>(flowExecutor, bufferCapacity)) {

            // 建立订阅关系（箭头表示数据流向），异步触发各订阅者的 onSubscribe
            MessageSubscriber subscriberA = new MessageSubscriber("Subscriber-A", latch);
            MessageSubscriber subscriberB = new MessageSubscriber("Subscriber-B", latch);
            publisher.subscribe(subscriberA); // A 订阅发布者：发布者 → A
            publisher.subscribe(subscriberB); // B 订阅发布者：发布者 → B

            // 【注意】先绑定订阅者，再发布消息，避免漏掉消息
            List<String> items = List.of("Data-1", "Data-2", "Data-3", "Data-4", "Data-5");
            log.info("====== 生产端开始异步发布数据 ======");
            for (String item : items) {
                log.info("[Publisher] 投入原始数据: {}", item);
                publisher.submit(item); // 提交消息，缓冲区满时阻塞
            }
        } // 自动关闭发布者，剩余消息消费完后触发 onComplete

        latch.await(); // 等待订阅结束
        flowExecutor.shutdown(); // 关闭线程池
        log.info("====== 基础发布订阅程序安全退出 ======");
    }

}
