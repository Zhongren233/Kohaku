package love.aira.kohaku.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.EventDispatcher;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.json.JsonMapper;

/** Spring 侧事件处理链：处理器 Bean 的收集与排序、CONSUMED 终止、未消费事件落到 @EventListener。 */
class KohakuEventDispatchTest {

    private static final List<String> CALLS = new CopyOnWriteArrayList<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, KohakuAutoConfiguration.class))
            .withPropertyValues("kohaku.qq.app-id=test-app", "kohaku.qq.app-secret=test-secret",
                    "kohaku.qq.auto-start=false");

    @BeforeEach
    void reset() {
        CALLS.clear();
    }

    @Test
    void collectsHandlerBeansAndConsumedEventStopsChain() {
        runner.withUserConfiguration(ConsumingAndIgnoringHandlers.class, FallbackRecorderConfiguration.class)
                .run(context -> {
                    EventDispatcher dispatcher = context.getBean(EventDispatcher.class);
                    assertThat(dispatcher.handlers()).extracting(handler -> handler.getClass().getSimpleName())
                            .containsExactly("ConsumingHandler", "IgnoringHandler");   // @Order(1) 在 @Order(2) 之前

                    dispatcher.dispatch(c2c());

                    assertThat(CALLS).containsExactly("consuming");                 // 消费后不再执行第二个
                    assertThat(context.getBean(FallbackRecorder.class).received).isEmpty();   // 也不再发布容器事件
                });
    }

    @Test
    void ignoredEventReachesEventListeners() {
        runner.withUserConfiguration(IgnoringHandlers.class, FallbackRecorderConfiguration.class).run(context -> {
            EventDispatcher dispatcher = context.getBean(EventDispatcher.class);
            dispatcher.dispatch(c2c());

            assertThat(CALLS).containsExactly("alpha", "beta");     // 全部忽略 → 顺序执行完
            assertThat(context.getBean(FallbackRecorder.class).received).hasSize(1);    // 兜底发布
        });
    }

    @Test
    void handlerOrderPropertyOverridesSpringOrder() {
        runner.withPropertyValues("kohaku.qq.handler-order=betaHandler,alphaHandler")
                .withUserConfiguration(IgnoringHandlers.class, FallbackRecorderConfiguration.class)
                .run(context -> {
                    assertThat(context.getBean(EventDispatcher.class).handlers())
                            .extracting(handler -> handler.getClass().getSimpleName())
                            .containsExactly("BetaHandler", "AlphaHandler");

                    context.getBean(EventDispatcher.class).dispatch(c2c());

                    assertThat(CALLS).containsExactly("beta", "alpha");
                });
    }

    @Test
    void unknownBeanNameInHandlerOrderFailsFast() {
        runner.withPropertyValues("kohaku.qq.handler-order=missingHandler")
                .withUserConfiguration(IgnoringHandlers.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("kohaku.qq.handler-order 中的 Bean 不存在: missingHandler");
                });
    }

    private static C2cMessageCreateEvent c2c() {
        return new C2cMessageCreateEvent(1, new JsonMapper().readTree("{}"),
                new C2cMessage("m1", null, "hi", null, 0, null, null, null, null));
    }

    /** 通用探针处理器：记录调用并返回指定结果。 */
    abstract static class ProbeHandler implements BotEventHandler<C2cMessageCreateEvent> {

        private final String name;
        private final HandlerResult result;

        ProbeHandler(String name, HandlerResult result) {
            this.name = name;
            this.result = result;
        }

        @Override
        public Class<C2cMessageCreateEvent> eventType() {
            return C2cMessageCreateEvent.class;
        }

        @Override
        public HandlerResult handle(C2cMessageCreateEvent event) {
            CALLS.add(name);
            return result;
        }
    }

    @Order(1)
    static class ConsumingHandler extends ProbeHandler {
        ConsumingHandler() {
            super("consuming", HandlerResult.CONSUMED);
        }
    }

    @Order(2)
    static class IgnoringHandler extends ProbeHandler {
        IgnoringHandler() {
            super("ignoring", HandlerResult.IGNORED);
        }
    }

    @Order(1)
    static class AlphaHandler extends ProbeHandler {
        AlphaHandler() {
            super("alpha", HandlerResult.IGNORED);
        }
    }

    @Order(2)
    static class BetaHandler extends ProbeHandler {
        BetaHandler() {
            super("beta", HandlerResult.IGNORED);
        }
    }

    /** 未被处理链消费的事件会作为容器事件到达这里。 */
    static class FallbackRecorder {

        final List<BotEvent> received = new CopyOnWriteArrayList<>();

        @EventListener
        void onBotEvent(BotEvent event) {
            received.add(event);
        }
    }

    static class ConsumingAndIgnoringHandlers {
        @Bean
        ConsumingHandler consumingHandler() {
            return new ConsumingHandler();
        }

        @Bean
        IgnoringHandler ignoringHandler() {
            return new IgnoringHandler();
        }
    }

    static class IgnoringHandlers {
        @Bean
        AlphaHandler alphaHandler() {
            return new AlphaHandler();
        }

        @Bean
        BetaHandler betaHandler() {
            return new BetaHandler();
        }
    }

    static class FallbackRecorderConfiguration {
        @Bean
        FallbackRecorder fallbackRecorder() {
            return new FallbackRecorder();
        }
    }
}
