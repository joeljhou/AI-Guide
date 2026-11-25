package cn.geekyspace.springreactive.reactor;

import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <a href="https://projectreactor.io/docs/core/release/reference/coreFeatures/programmatically-creating-sequence.html">以编程方式创建序列：generate、create、push 与 handle</a>
 * <p>
 * Reactor Core 文档示例：generate、create、push、混合推送/拉取、资源清理和 handle。
 * 不启动 Spring Boot，直接运行 main 即可依次执行全部示例。
 */
public class ProgrammaticSequence {

    public static void main(String[] args) throws Exception {
        // 默认依次执行全部示例；只看某一节时，注释掉其他调用即可。
        lesson1Generate();      // 一、同步 generate
        lesson2Create();        // 二、异步和多线程： create
        lesson3Overflow();      // 三、背压溢出策略： OverflowStrategy
        lesson4Push();          // 四、单线程生产者： push
        lesson5PushPull();      // 五、混合推送/拉取
        lesson6Cleanup();       // 六、取消与资源释放
        lesson7Handle();        // 七、映射与过滤
    }

    // 1. generate：每轮回调最多调用一次 next，可以再调用 complete 或 error。
    private static void lesson1Generate() {
        chapter("1. generate：同步逐个生成");

        demo("不可变状态：3 的乘法表，从 0 到 10");
        Flux.<String, Integer>generate(() -> 0, // 初始 state
                (state, sink) -> {
                    // sink：每轮只能 next() 一次
                    sink.next("3 x " + state + " = " + 3 * state);
                    if (state == 3) sink.complete();
                    return state + 1; // 返回值即下一轮的 state
                }).subscribe(ProgrammaticSequence::printValue, ProgrammaticSequence::printError, () -> System.out.println("onComplete"));

        demo("可变状态与清理：返回同一个 AtomicLong，最终状态为 6");
        multiplicationTable().subscribe(ProgrammaticSequence::printValue);

        demo("提前取消也会清理状态：take(3) 后最终状态为 3");
        multiplicationTable().take(3).subscribe(ProgrammaticSequence::printValue);

        demo("generate 按需生成：request(2) 只执行两轮，再取消");
        BaseSubscriber<Integer> subscriber = manualSubscriber();
        Flux.<Integer, Integer>generate(() -> 0, (state, sink) -> {
            System.out.println("生成第 " + state + " 个元素");
            sink.next(state);
            return state + 1;
        }, state -> System.out.println("清理最终状态: " + state)).subscribe(subscriber);
        subscriber.request(2);
        subscriber.cancel();
    }

    // 3 的乘法表，从 0 到 5
    private static Flux<String> multiplicationTable() {
        return Flux.<String, AtomicLong>generate(AtomicLong::new, (state, sink) -> {
            long i = state.getAndIncrement();
            sink.next("3 x " + i + " = " + 3 * i);
            if (i == 3) sink.complete();
            return state;
        }, (state) -> System.out.println("清理最终状态: " + state.get()));
    }

    // 2. create：一次回调可以发出多个元素，也允许多个生产线程调用 sink。
    private static void lesson2Create() throws Exception {
        chapter("2. create：桥接异步和多线程事件");

        demo("把监听器的数据块拆成元素；两个生产者的输出顺序不作保证");
        EventProcessor<String> processor = new EventProcessor<>();
        Flux<String> bridge = Flux.create(sink -> registerListener(processor, sink));
        Disposable subscription = bridge.subscribe(ProgrammaticSequence::printValue, ProgrammaticSequence::printError, () -> System.out.println("onComplete"));

        try (ExecutorService workers = Executors.newFixedThreadPool(2,        // 创建固定数量线程池
                Thread.ofPlatform().name("create-producer-", 1).factory()  // JDK 21 Thread API
        )) {
            // 循环屏障，2 个线程都调用 await() 后才继续执行
            CyclicBarrier start = new CyclicBarrier(2);
            Future<?> first = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                processor.emit(List.of("A-1", "A-2"));
                return null;
            });
            Future<?> second = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                processor.emit(List.of("B-1", "B-2"));
                return null;
            });
            // 等两个生产者都发完后再完成，避免提前结束导致另一个生产者的数据被丢弃。
            await(first);
            await(second);
            processor.complete();
        } finally {
            subscription.dispose();
        }

        demo("Mono.create：success(value) 同时发出值并完成，后续 success 被忽略");
        Mono.<String>create(sink -> {
            sink.success("第一次结果");
            sink.success("第二次结果：不会传给下游");
        }).subscribe(ProgrammaticSequence::printValue, ProgrammaticSequence::printError, () -> System.out.println("onComplete"));
    }

    // 3. 不请求也能收到终止信号；有缓冲数据时，完成信号要等缓冲数据消费完。
    private static void lesson3Overflow() {
        chapter("3. create 的背压溢出策略");
        System.out.println("每种策略都先生产 1、2、3，再调用 request(3)。");

        for (FluxSink.OverflowStrategy strategy : FluxSink.OverflowStrategy.values()) {
            String description = switch (strategy) {
                case IGNORE -> "不管下游，照发；队列满了会报错";
                case ERROR -> "下游接不住，直接报错";
                case DROP -> "下游接不住，新数据直接丢掉";
                case LATEST -> "下游接不住，只留最新一条，旧的丢掉";
                case BUFFER -> "默认策略。下游接不住，先全部存起来，存太多会内存溢出";
            };
            demo(strategy + "：" + description);
            BaseSubscriber<Integer> subscriber = manualSubscriber();
            Flux.<Integer>create(sink -> {
                for (int i = 1; i <= 3 && !sink.isCancelled(); i++) {
                    System.out.println("生产: " + i);
                    sink.next(i);
                }
                sink.complete();
            }, strategy).subscribe(subscriber);
            System.out.println("现在 request(3)");
            subscriber.request(3);
        }
    }

    // 4. push：可桥接异步事件，但生产端的 next/error/complete 不能并发调用。
    private static void lesson4Push() throws Exception {
        chapter("4. push：异步但单线程生产");

        demo("同一个事件线程发出数据和完成信号");
        runSingleProducer(false);

        demo("同一个事件线程发出数据和错误信号");
        runSingleProducer(true);
    }

    private static void runSingleProducer(boolean fail) throws Exception {
        EventProcessor<String> processor = new EventProcessor<>();
        Flux<String> bridge = Flux.push(sink -> registerListener(processor, sink));
        Disposable subscription = bridge.subscribe(ProgrammaticSequence::printValue, ProgrammaticSequence::printError, () -> System.out.println("onComplete"));
        try (ExecutorService worker = Executors.newSingleThreadExecutor(  // 创建单线程池
                Thread.ofPlatform().name("push-producer").factory()       // JDK 21 Thread API
        )) {
            await(worker.submit(() -> {
                processor.emit(List.of("push-1", "push-2"));
                if (fail) {
                    processor.fail(new IllegalStateException("模拟事件源异常"));
                } else {
                    processor.complete();
                }
            }));
        } finally {
            subscription.dispose();
        }
    }

    // 5. request 拉取历史数据，监听器推送新数据；二者共享下游需求。
    private static void lesson5PushPull() {
        chapter("5. 混合推送/拉取模型");

        demo("onRequest 拉取历史消息，监听器接收实时消息");
        MessageProcessor processor = new MessageProcessor(List.of("历史-1", "历史-2"));
        Flux<String> bridge = Flux.create(sink -> {
            registerListener(processor, sink);
            // 注册 onRequest 回调，在下游向 FluxSink 请求数据时收到通知
            sink.onRequest(n -> {
                System.out.println("onRequest(" + n + ")：拉取尚未读取的历史消息");
                for (String message : processor.getHistory(n)) {
                    sink.next(message); // 发送历史消息
                }
            });
        });
        BaseSubscriber<String> subscriber = manualSubscriber();
        bridge.subscribe(subscriber);

        System.out.println("request(2)：收到两条历史消息");
        subscriber.request(2);
        System.out.println("需求耗尽，实时消息先进入 create 的默认缓冲区");
        processor.emit(List.of("实时-3"));
        System.out.println("request(1)：历史已读完，消费缓冲的实时消息");
        subscriber.request(1);
        processor.complete();

        // create 的 onRequest 收到实际需求；push 的 onRequest 只收到一次 Long.MAX_VALUE。
        // 此处事件源由主线程顺序触发，让需求与缓冲关系可重复观察。
    }

    // 6. onCancel 仅处理取消，onDispose 处理完成、错误和取消。
    private static void lesson6Cleanup() {
        chapter("6. onCancel 与 onDispose：资源清理");

        for (String termination : List.of("complete", "error", "cancel")) {
            demo(termination + "：观察清理回调");
            Disposable subscription = Flux.<String>create(sink -> {
                sink.onCancel(() -> System.out.println("onCancel：停止外部任务"));
                sink.onDispose(() -> System.out.println("onDispose：关闭通道、移除监听器"));
                sink.next("通道已打开");
                if ("complete".equals(termination)) {
                    sink.complete();
                } else if ("error".equals(termination)) {
                    sink.error(new IllegalStateException("模拟通道异常"));
                }
                // cancel 分支保持订阅打开，稍后由调用方取消。
            }).subscribe(ProgrammaticSequence::printValue, ProgrammaticSequence::printError, () -> System.out.println("onComplete"));

            if ("cancel".equals(termination)) {
                subscription.dispose(); // 先 onCancel，再 onDispose；不会发出 onComplete
            }
        }
    }

    // 7. handle：对已有源逐个处理，每个输入最多输出一个元素，不调用 next 即过滤。
    private static void lesson7Handle() {
        chapter("7. handle：映射与过滤");

        demo("把数字转成字母并过滤 null，输出 M、I、T");
        Flux.just(-1, 30, 13, 9, 20) // null、null、M、I、T
                .<String>handle((i, sink) -> {
            String letter = alphabet(i);
            if (letter != null) {
                sink.next(letter); // 不允许 sink.next(null)
            }
        }).subscribe(ProgrammaticSequence::printValue, ProgrammaticSequence::printError, () -> System.out.println("onComplete"));

        demo("直接 map 返回 null 会报错");
        Flux.just(-1, 13).map(ProgrammaticSequence::alphabet)
                .subscribe(ProgrammaticSequence::printValue, ProgrammaticSequence::printError);

        demo("handle 也能提前完成：遇到 3 时停止，后续元素不再处理");
        Flux.range(1, 5).<Integer>handle((value, sink) -> {
            if (value == 3) {
                sink.complete();
            } else {
                sink.next(value);
            }
        }).subscribe(ProgrammaticSequence::printValue, ProgrammaticSequence::printError, () -> System.out.println("onComplete"));
    }

    // 辅助方法：1-26 对应 A-Z，其他数字返回 null
    private static String alphabet(int number) {
        return number >= 1 && number <= 26
                ? String.valueOf((char) ('A' + number - 1))
                : null;
    }

    /**
     * 将同一套监听器 API 分别桥接到 create 和 push，终止时解除注册。
     */
    private static <T> void registerListener(EventProcessor<T> processor, FluxSink<T> sink) {
        // 实现 MyEventListener 接口
        EventListener<T> listener = new EventListener<>() {
            @Override
            public void onDataChunk(List<T> chunk) {
                for (T value : chunk) {
                    if (sink.isCancelled()) break;  // 取消感知
                    sink.next(value);               // 对应 onNext
                }
            }


            @Override
            public void processComplete() {sink.complete();} // 对应 onComplete

            @Override
            public void processError(Throwable error) {sink.error(error);}
        };
        // 注册事件监听器
        processor.register(listener);
        // 销毁时移除事件监听器
        sink.onDispose(() -> {
            processor.unregister(listener);
            System.out.println("onDispose：已移除事件监听器");
        });
    }

    private interface EventListener<T> {
        // 数据块已就绪
        void onDataChunk(List<T> chunk);

        // 处理已完成（终止事件）
        void processComplete();

        // 处理已失败（终止事件）
        void processError(Throwable error);
    }

    /**
     * 模拟只支持一个监听器的外部 API；每个示例单独创建一个事件源。
     */
    private static class EventProcessor<T> {
        private final AtomicReference<EventListener<T>> listener = new AtomicReference<>();

        // 注册事件监听器
        void register(EventListener<T> next) {
            if (!listener.compareAndSet(null, next)) {
                throw new IllegalStateException("此示例事件源只支持一个监听器");
            }
        }

        // 移除事件监听器
        void unregister(EventListener<T> current) {
            listener.compareAndSet(current, null);
        }

        // 发送事件块
        void emit(List<T> chunk) {
            EventListener<T> current = listener.get();
            if (current != null) {
                System.out.println("[" + Thread.currentThread().getName() + "] 事件块: " + chunk);
                current.onDataChunk(chunk);
            }
        }

        // 发送完成事件
        void complete() {
            EventListener<T> current = listener.get();
            if (current != null) {
                current.processComplete();
            }
        }

        // 发送失败事件
        void fail(Throwable error) {
            EventListener<T> current = listener.get();
            if (current != null) {
                current.processError(error);
            }
        }
    }

    /**
     * 历史消息读取后移除，后续 request 不会重复拉取。
     */
    private static class MessageProcessor extends EventProcessor<String> {
        private final Queue<String> history;

        MessageProcessor(List<String> history) {
            this.history = new ArrayDeque<>(history);
        }

        synchronized List<String> getHistory(long demand) {
            List<String> messages = new ArrayList<>();
            while (demand > 0 && !history.isEmpty()) {
                messages.add(history.remove());
                demand--;
            }
            return messages;
        }
    }

    /**
     * 不发送初始需求，由示例代码显式 request，避免默认的无限请求掩盖背压。
     */
    private static <T> BaseSubscriber<T> manualSubscriber() {
        return new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                System.out.println("已订阅，暂不 request");
            }

            @Override
            protected void hookOnNext(T value) {
                printValue(value);
            }

            @Override
            protected void hookOnComplete() {
                System.out.println("onComplete");
            }

            @Override
            protected void hookOnError(Throwable error) {
                printError(error);
            }
        };
    }

    private static void await(Future<?> task) throws Exception {
        task.get(5, TimeUnit.SECONDS); // 等待真实任务结束，不靠 sleep 猜测完成时间
    }

    private static void chapter(String title) {
        System.out.println("\n========== " + title + " ==========");
    }

    private static void demo(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    private static void printValue(Object value) {
        System.out.println("收到: " + value);
    }

    private static void printError(Throwable error) {
        System.out.println("错误: " + error);
    }

}
