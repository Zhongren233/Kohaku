package love.aira.kohaku.gateway.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
    private final List<BotEvent> fallback = new ArrayList<>();

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
                c2cHandler("second", HandlerResult.IGNORED)), fallback::add)
                .dispatch(c2c());

        assertThat(calls).containsExactly("first", "second");
        assertThat(fallback).hasSize(1);   // 全部忽略 → 交给兜底
    }

    @Test
    void stopsAtFirstConsumedHandler() {
        new EventDispatcher(List.of(c2cHandler("first", HandlerResult.CONSUMED),
                c2cHandler("second", HandlerResult.IGNORED)), fallback::add)
                .dispatch(c2c());

        assertThat(calls).containsExactly("first");
        assertThat(fallback).isEmpty();    // 已消费 → 不兜底
    }

    @Test
    void skipsHandlersWhoseTypeDoesNotMatch() {
        GroupAtMessageCreateEvent event = group();
        new EventDispatcher(List.of(c2cHandler("c2c", HandlerResult.CONSUMED)), fallback::add).dispatch(event);

        assertThat(calls).isEmpty();
        assertThat(fallback).containsExactly(event);
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

        new EventDispatcher(List.of(broken, c2cHandler("after", HandlerResult.CONSUMED)), fallback::add).dispatch(c2c());

        assertThat(calls).containsExactly("broken", "after");   // 异常后继续执行下一个
        assertThat(fallback).isEmpty();                          // 后续处理器消费了它
    }

    @Test
    void withoutHandlersEverythingFallsBack() {
        new EventDispatcher(List.of(), fallback::add).dispatch(c2c());

        assertThat(fallback).hasSize(1);
    }

    @Test
    void withoutFallbackConsumedAndIgnoredBothEndSilently() {
        new EventDispatcher(List.of(c2cHandler("only", HandlerResult.IGNORED))).dispatch(c2c());

        assertThat(calls).containsExactly("only");
    }
}
