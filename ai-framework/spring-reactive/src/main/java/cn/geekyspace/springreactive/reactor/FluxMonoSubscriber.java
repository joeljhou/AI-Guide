package cn.geekyspace.springreactive.reactor;

import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <a href="https://projectreactor.io/docs/core/release/reference/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html">创建 Flux 或 Mono 并订阅它</a>
 * <p>
 * Reactor Core 文档示例：创建、订阅、取消、背压和需求重塑。
 * 不启动 Spring Boot，直接运行 main 即可依次执行全部示例。
 */
public class FluxMonoSubscriber {

    public static void main(String[] args) {
        // 默认依次执行全部示例；只看某一节时，注释掉其他调用即可。
        lesson1Create();               // 一、创建 Flux 和 Mono
        lesson2Subscribe();            // 二、subscribe 的回调组合
        lesson3Disposable();           // 三、Disposable 取消订阅
        lesson4BaseSubscriber();       // 四、BaseSubscriber 精细控制
        lesson5SubscriptionPatterns(); // 五、订阅模式
        lesson6Backpressure();         // 六、背压
        lesson7DemandReshaping();      // 七、需求重塑
    }

    // 1. 创建 Flux 和 Mono
    private static void lesson1Create() {
        chapter("1. 创建 Flux 和 Mono");

        demo("Flux.just / Flux.fromIterable");
        Flux.just("foo", "bar", "foobar").subscribe(FluxMonoSubscriber::printValue);
        Flux.fromIterable(List.of("foo", "bar", "foobar")).subscribe(FluxMonoSubscriber::printValue);

        demo("Mono.empty：没有元素，但会正常完成");
        Mono.<String>empty().subscribe(
                value -> System.out.println("这行不会执行: " + value),
                FluxMonoSubscriber::printError,
                () -> System.out.println("没有 onNext，直接 onComplete")
        );

        demo("Mono.just：一个元素，然后完成");
        Mono.just("foo").subscribe(
                FluxMonoSubscriber::printValue,
                FluxMonoSubscriber::printError,
                () -> System.out.println("onComplete")
        );

        demo("Flux.range(5, 3)：输出 5、6、7");
        Flux.range(5, 3).subscribe(System.out::println);
    }

    // 2. subscribe 的回调组合
    private static void lesson2Subscribe() {
        chapter("2. subscribe 的回调组合");

        demo("subscribe()：启动序列，但不打印元素");
        Flux.range(1, 3).subscribe();
        System.out.println("序列已经执行。没有元素输出，不等于没有执行。");

        demo("subscribe(onNext)");
        Flux.range(1, 3).subscribe(value -> System.out.println("onNext: " + value));

        demo("subscribe(onNext, onError)");
        failAtFour().subscribe(
                value -> System.out.println("onNext: " + value),
                error -> System.out.println("onError: " + error.getMessage())
        );

        demo("subscribe(onNext, onError, onComplete)");
        Flux.range(1, 4).subscribe(
                value -> System.out.println("onNext: " + value),
                error -> System.out.println("onError: " + error.getMessage()),
                () -> System.out.println("onComplete: Done")
        );

        demo("四参数 subscribe：自己提供初始 request（已废弃）");
        subscribeRequest(Flux.range(1, 3), 3);

        demo("subscribeWith + BaseSubscriber：手动控制初始 request");
        subscribeWithBaseSubscriber(Flux.range(1, 3), 3);

        demo("onError 与 onComplete 互斥");
        failAtFour().subscribe(
                value -> System.out.println("onNext: " + value),
                error -> System.out.println("onError: " + error.getMessage()),
                () -> System.out.println("这行不会执行：错误后不会再正常完成")
        );
    }

    /**
     * 保留文档中的四参数 subscribe 示例；该重载在当前版本已弃用。
     */
    @SuppressWarnings("deprecation")
    private static void subscribeRequest(Flux<Integer> source, long demand) {
        source.subscribe(
                value -> System.out.println("onNext: " + value),
                error -> System.out.println("onError: " + error.getMessage()),
                () -> System.out.println("onComplete: Done"),
                subscription -> {
                    System.out.println("onSubscribe: request(" + demand + ")");
                    subscription.request(demand);
                }
        );
    }

    /**
     * 使用 subscribeWith + BaseSubscriber 替代已弃用的四参数 subscribe。
     */
    private static void subscribeWithBaseSubscriber(Flux<Integer> source, long demand) {
        source.subscribeWith(new BaseSubscriber<Integer>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                System.out.println("onSubscribe: request(" + demand + ")");
                request(demand);
            }

            @Override
            protected void hookOnNext(Integer value) {
                System.out.println("onNext: " + value);
            }

            @Override
            protected void hookOnError(Throwable error) {
                System.out.println("onError: " + error.getMessage());
            }

            @Override
            protected void hookOnComplete() {
                System.out.println("onComplete: Done");
            }
        });
    }

    private static Flux<Integer> failAtFour() {
        return Flux.range(1, 4).map(value -> {
            if (value == 4) throw new IllegalStateException("Got to 4");
            return value;
        });
    }

    // 3. 取消、替换和批量释放订阅
    private static void lesson3Disposable() {
        chapter("3. 取消、替换和批量释放订阅");

        demo("Disposable.dispose：取消尚未结束的订阅");
        Disposable handle = openSubscription("单个订阅");
        System.out.println("准备取消，此时还没收到 3 到 10");
        handle.dispose();
        System.out.println("isDisposed: " + handle.isDisposed());

        demo("Disposables.swap：替换时取消旧订阅");
        Disposable.Swap slot = Disposables.swap();
        slot.update(openSubscription("旧请求"));
        slot.update(openSubscription("新请求"));
        System.out.println("关闭 slot：取消新请求");
        slot.dispose();
        System.out.println("向已关闭的 slot 放入请求：立即取消");
        slot.update(openSubscription("关闭后提交的请求"));

        demo("Disposables.composite：批量取消");
        Disposable.Composite group = Disposables.composite();
        group.add(openSubscription("请求 A"));
        group.add(openSubscription("请求 B"));
        System.out.println("关闭 group：A、B 都收到 cancel，顺序不作保证");
        group.dispose();
        System.out.println("往已关闭的 group 添加请求：立即取消");
        group.add(openSubscription("请求 C"));

        demo("同步源可能在 subscribe 返回前就完成");
        Disposable completed = Flux.range(1, 3)
                .doOnCancel(() -> System.out.println("这行不会执行：源已经完成"))
                .subscribe(System.out::println,
                        FluxMonoSubscriber::printError,
                        () -> System.out.println("已正常完成"));
        completed.dispose(); // 不能撤回已发出的 1、2、3
    }

    /**
     * 只请求 2 个，保持订阅未完成，便于确定性地观察取消。
     */
    @SuppressWarnings("deprecation")
    private static Disposable openSubscription(String name) {
        return Flux.range(1, 10)
                .doOnCancel(() -> System.out.println(name + " <- cancel"))
                .subscribe(
                        value -> System.out.println(name + " <- " + value),
                        error -> System.out.println(name + " <- error: " + error),
                        () -> System.out.println(name + " <- complete"),
                        subscription -> subscription.request(2)
                );
    }

    // 4.方案：BaseSubscriber 请求和生命周期
    private static void lesson4BaseSubscriber() {
        chapter("4. BaseSubscriber：请求和生命周期");

        demo("正常完成：Subscribed -> 1、2、3、4 -> Complete -> Finally");
        Flux.range(1, 4).subscribe(new SampleSubscriber<>());

        demo("发生错误：Error -> Finally，不会调用 Complete");
        failAtFour().subscribe(new SampleSubscriber<>());
    }

    private static class SampleSubscriber<T> extends BaseSubscriber<T> {
        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            System.out.println("Subscribed: request(1)");
            request(1); // 重写此钩子后，不再使用默认的无限请求
        }

        @Override
        protected void hookOnNext(T value) {
            System.out.println("收到: " + value);
            request(1); // 处理完一个，再请求一个
        }

        @Override
        protected void hookOnComplete() {
            System.out.println("Complete");
        }

        @Override
        protected void hookOnError(Throwable error) {
            System.out.println("Error: " + error.getMessage());
        }

        @Override
        protected void hookOnCancel() {
            System.out.println("Cancel");
        }

        @Override
        protected void hookFinally(SignalType type) {
            System.out.println("Finally: " + type);
        }
    }

    // 5. 订阅模式
    private static void lesson5SubscriptionPatterns() {
        chapter("5. 订阅模式");

        demo("反例：方法内部直接 subscribe");
        Mono<Void> detached = fireAndForget("A");
        System.out.println("还没订阅返回值，A 的副作用却已经执行了");
        detached.subscribe(); // 订阅的是另一个 empty，与内部订阅无关

        demo("首选：组合并返回，把启动权交给调用方");
        Mono<Void> composed = composeAndReturn("B");
        System.out.println("链已组装，此时 B 的副作用尚未执行");
        composed.subscribe(
                ignored -> {/* handle next */},
                error -> System.out.println("调用方收到错误: " + error.getMessage()),
                () -> System.out.println("调用方收到完成")
        );

        demo("命令式事件入口：显式订阅时处理错误");
        doSomething("FAIL").subscribe(
                ignored -> {/* handle next */},
                error -> System.out.println("事件处理失败: " + error.getMessage())
        );

        demo("保存 Disposable，稍后清理");
        AtomicReference<Disposable> ref = new AtomicReference<>();
        ref.set(openSubscription("可清理任务"));
        System.out.println("模拟组件退出，清理保存的句柄");
        ref.get().dispose();
    }

    // 即发即忘 -> 这个内部订阅是独立的"野线程"，调用者拿不到它
    private static Mono<Void> fireAndForget(String value) {
        doSomething(value).subscribe(
                result -> System.out.println("内部订阅拿到: " + result),
                error -> System.out.println("内部订阅错误: " + error.getMessage())
        );
        return Mono.empty(); // 调用方无法协调内部任务的错误和取消
    }


    private static Mono<Void> composeAndReturn(String value) {
        return doSomething(value).then(); // 保留完成、错误及取消的传播
    }

    private static Mono<String> doSomething(String value) {
        return Mono.just(value).map(input -> {
            if ("FAIL".equals(input)) {
                throw new IllegalStateException("模拟服务异常");
            }
            System.out.println("执行副作用: " + input);
            return "已处理 " + input;
        });
    }

    // 6. 背压：request 与取消
    private static void lesson6Backpressure() {
        chapter("6. 背压：request 与取消");

        demo("普通 lambda 订阅默认请求 Long.MAX_VALUE");
        Flux.range(1, 3)
                .doOnRequest(n -> System.out.println("源收到 request(" + n + ")"))
                .subscribe(value -> System.out.println("收到: " + value));

        demo("只要 1 个，然后取消");
        Flux.range(1, 10)
                .doOnRequest(n -> System.out.println("源收到 request(" + n + ")"))
                .subscribe(new BaseSubscriber<>() {
                    @Override
                    protected void hookOnSubscribe(Subscription subscription) {
                        request(1);
                    }

                    @Override
                    protected void hookOnNext(Integer value) {
                        System.out.println("收到 " + value + "，现在 cancel()");
                        cancel();
                    }

                    @Override
                    protected void hookOnCancel() {
                        System.out.println("hookOnCancel");
                    }

                    @Override
                    protected void hookFinally(SignalType type) {
                        System.out.println("hookFinally: " + type);
                    }
                });

        demo("不请求 -> 暂停；补充 request -> 继续");
        BaseSubscriber<Integer> subscriber = new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                System.out.println("已经订阅，故意不发初始 request");
            }

            @Override
            protected void hookOnNext(Integer value) {
                System.out.println("收到: " + value);
            }

            @Override
            protected void hookOnComplete() {
                System.out.println("全部完成");
            }
        };
        Flux.range(1, 4)
                .doOnRequest(n -> System.out.println("源收到 request(" + n + ")"))
                .subscribe(subscriber);
        System.out.println("没有元素输出；现在 request(2)");
        subscriber.request(2);
        System.out.println("只收到 1、2；再 request(2)");
        subscriber.request(2);
    }

    // 7. 操作符如何重塑需求
    private static void lesson7DemandReshaping() {
        chapter("7. 操作符如何重塑需求");

        demo("buffer(3)：下游要 2 批，源收到 request(6)");
        Flux.range(1, 10)
                .doOnRequest(n -> System.out.println("原始源 request(" + n + ")"))
                .buffer(3) // 每批 3 个元素
                .doOnRequest(n -> System.out.println("buffer 下游 request(" + n + ")"))
                .subscribe(new BaseSubscriber<>() {
                    private int received;

                    @Override
                    protected void hookOnSubscribe(Subscription subscription) {
                        request(2); // 订阅者请求两批（每批是一个 List）
                    }

                    @Override
                    protected void hookOnNext(List<Integer> batch) {
                        System.out.println("收到一批: " + batch);
                        if (++received == 2) {
                            cancel(); // 订阅者收到两批后，取消订阅
                        }
                    }
                });

        demo("flatMap 内部序列：prefetch=4，消费 3 个后补充 3 个");
        Flux.range(1, 2)
                .flatMap(group -> Flux.range(group * 10, 8)
                                .doOnRequest(n -> System.out.println("内部序列 " + group + " request(" + n + ")")),
                        1, 4) // concurrency=1，prefetch=4
                .subscribe(value -> System.out.println("收到: " + value));

        demo("limitRate(4)：初始 4，按 75% 策略补充 3");
        Flux.range(1, 10)
                .doOnRequest(n -> System.out.println("原始源 request(" + n + ")"))
                .limitRate(4)
                .doOnRequest(n -> System.out.println("limitRate 下游 request(" + n + ")"))
                .subscribe(value -> System.out.println("收到: " + value));

        demo("limitRate(4, 0)：严格按每批 4 个向上游请求");
        Flux.range(1, 10)
                .doOnRequest(n -> System.out.println("原始源 request(" + n + ")"))
                .limitRate(4, 0)
                .subscribe(value -> System.out.println("收到: " + value));

        demo("take(5, true)：limitRequest(5) 的现代替代");
        Flux.range(1, 10)
                .doOnRequest(n -> System.out.println("原始源 request(" + n + ")"))
                .doOnCancel(() -> System.out.println("原始源收到 cancel"))
                .take(5, true)
                .subscribe(
                        value -> System.out.println("收到: " + value),
                        FluxMonoSubscriber::printError,
                        () -> System.out.println("下游 onComplete：达到总量上限")
                );
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