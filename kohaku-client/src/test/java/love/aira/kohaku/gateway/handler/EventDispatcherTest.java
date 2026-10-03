package love.aira.kohaku.gateway.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import love.aira.kohaku.gateway.event.BotDispatchEvent;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EventDispatcherTest {

    private final List<String> calls = new ArrayList<>();
    private final List<BotEvent> observed = new ArrayList<>();

    private static C2cMessageCreateEvent c2c() {
        return new C2cMessageCreateEvent(1, "C2C_MESSAGE_CREATE:ID", new JsonMapper().readTree("{}"),
                new C2cMessage("m1", null, "hi", null, 0, null, null, null, null));
    }

    private static GroupAtMessageCreateEvent group() {
        return new GroupAtMessageCreateEvent(2, "GROUP_AT_MESSAGE_CREATE:ID", new JsonMapper().readTree("{}"),
                new GroupMessage("m2", null, "hi", "G1", null, 0, null, null, null, null, null));
    }

    private BotEventHandler<C2cMessageCreateEvent> c2cHandler(String name, HandlerResult result) {
        return new BotEventHandler<>() {
            @Override
            public Class<C2cMessageCreateEvent> eventType() {
                return C2cMessageCreateEvent.class;
            }

            @Override
            public HandlerResult handle(C2cMessageCreateEvent event) {
                calls.add(name);
                return result;
            }
        };
    }

    @Test
    void executesHandlersInDeclaredOrderAndContinuesOnIgnored() {
        new EventDispatcher(List.of(c2cHandler("first", HandlerResult.IGNORED),
                c2cHandler("second", HandlerResult.IGNORED)), observed::add)
                .dispatch(c2c());

        assertThat(calls).containsExactly("first", "second");
        assertThat(observed).hasSize(1);   // 全部忽略 → 观察者同样收到
    }

    @Test
    void stopsAtFirstConsumedHandler() {
        new EventDispatcher(List.of(c2cHandler("first", HandlerResult.CONSUMED),
                c2cHandler("second", HandlerResult.IGNORED)), observed::add)
                .dispatch(c2c());

        assertThat(calls).containsExactly("first");   // 消费终止链内后续处理器
        assertThat(observed).hasSize(1);              // 但观察者仍收到
    }

    @Test
    void observerRunsAfterChain() {
        new EventDispatcher(List.of(c2cHandler("first", HandlerResult.CONSUMED),
                c2cHandler("second", HandlerResult.IGNORED)), event -> calls.add("observer"))
                .dispatch(c2c());

        assertThat(calls).containsExactly("first", "observer");   // 消费只终止链，观察者仍在链后执行
    }

    @Test
    void observerReceivesEveryEventInOrder() {
        EventDispatcher dispatcher = new EventDispatcher(
                List.of(c2cHandler("consumer", HandlerResult.CONSUMED)), observed::add);

        C2cMessageCreateEvent first = c2c();
        C2cMessageCreateEvent second = c2c();
        dispatcher.dispatch(first);
        dispatcher.dispatch(second);

        assertThat(observed).containsExactly(first, second);   // 无条件、按事件先后顺序
    }

    @Test
    void skipsHandlersWhoseTypeDoesNotMatch() {
        GroupAtMessageCreateEvent event = group();
        new EventDispatcher(List.of(c2cHandler("c2c", HandlerResult.CONSUMED)), observed::add).dispatch(event);

        assertThat(calls).isEmpty();
        assertThat(observed).containsExactly(event);
    }

    @Test
    void parentTypeHandlerReceivesSubtypes() {
        List<String> seen = new ArrayList<>();
        BotEventHandler<BotDispatchEvent> catchAll = new BotEventHandler<>() {
            @Override
            public Class<BotDispatchEvent> eventType() {
                return BotDispatchEvent.class;
            }

            @Override
            public HandlerResult handle(BotDispatchEvent event) {
                seen.add(event.type());
                return HandlerResult.IGNORED;
            }
        };

        new EventDispatcher(List.of(catchAll)).dispatch(c2c());

        assertThat(seen).containsExactly("C2C_MESSAGE_CREATE");
    }

    @Test
    void handlerFailureIsTreatedAsIgnored() {
        BotEventHandler<C2cMessageCreateEvent> broken = new BotEventHandler<>() {
            @Override
            public Class<C2cMessageCreateEvent> eventType() {
                return C2cMessageCreateEvent.class;
            }

            @Override
            public HandlerResult handle(C2cMessageCreateEvent event) {
                calls.add("broken");
                throw new IllegalStateException("boom");
            }
        };

        new EventDispatcher(List.of(broken, c2cHandler("after", HandlerResult.CONSUMED)), observed::add).dispatch(c2c());

        assertThat(calls).containsExactly("broken", "after");   // 异常后继续执行下一个
        assertThat(observed).hasSize(1);                         // 链异常/消费都不影响观察者
    }

    @Test
    void withoutHandlersObserverStillReceives() {
        new EventDispatcher(List.of(), observed::add).dispatch(c2c());

        assertThat(observed).hasSize(1);
    }

    @Test
    void withoutObserverConsumedAndIgnoredBothEndSilently() {
        new EventDispatcher(List.of(c2cHandler("only", HandlerResult.IGNORED))).dispatch(c2c());

        assertThat(calls).containsExactly("only");
    }

    @Test
    void consumedWithActionRunsItOnExecutorAndStopsChain() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-worker"));
        try {
            List<String> worker = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> threadName = new AtomicReference<>();
            BotEventHandler<C2cMessageCreateEvent> slow = new BotEventHandler<>() {
                @Override
                public Class<C2cMessageCreateEvent> eventType() {
                    return C2cMessageCreateEvent.class;
                }

                @Override
                public HandlerResult handle(C2cMessageCreateEvent event) {
                    calls.add("slow");
                    return HandlerResult.consumed(() -> {
                        threadName.set(Thread.currentThread().getName());
                        worker.add("work");
                        done.countDown();
                    });
                }
            };

            new EventDispatcher(List.of(slow, c2cHandler("second", HandlerResult.IGNORED)), observed::add, executor)
                    .dispatch(c2c());

            assertThat(calls).containsExactly("slow");             // 与 CONSUMED 一样终止链内后续处理器
            assertThat(observed).hasSize(1);                        // 观察者仍在链后收到事件
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();   // 副作用异步执行
            assertThat(worker).containsExactly("work");
            assertThat(threadName.get()).isEqualTo("test-worker");  // 确实在 worker 线程上
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void consumedWithActionRunsInlineWithoutExecutor() {
        List<String> worker = new ArrayList<>();

        new EventDispatcher(List.of(c2cHandler("slow", HandlerResult.consumed(() -> worker.add("work")))),
                observed::add).dispatch(c2c());

        assertThat(worker).containsExactly("work");   // 无执行器 → 当前线程同步执行
        assertThat(observed).hasSize(1);
    }
}
