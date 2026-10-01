package love.aira.kohaku.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.gateway.event.model.InteractionData;
import love.aira.kohaku.gateway.event.model.InteractionResolved;
import love.aira.kohaku.gateway.event.model.MessageAuthor;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.EventDispatcher;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 函数式装配：{@code BotFeature.of(...).message(...).button(...).build()} 与两个 lambda 工厂。
 *
 * <p>断言的是装配结果（handler 列表的实际行为）与端到端路由，不看内部字段。
 */
class BotFeatureTest {

    @Test
    void buildsFeatureFromLambdas() {
        BotFeature feature = BotFeature.of("card")
                .message(C2cMessageCreateEvent.class, event -> HandlerResult.CONSUMED)
                .button("next", context -> HandlerResult.CONSUMED)
                .build();

        assertThat(feature.id()).isEqualTo("card");
        assertThat(feature.messageHandlers()).hasSize(1);
        assertThat(feature.messageHandlers().getFirst().eventType()).isEqualTo(C2cMessageCreateEvent.class);
        assertThat(feature.buttonHandlers()).hasSize(1);
        assertThat(feature.buttonHandlers().getFirst().action()).isEqualTo("next");
    }

    @Test
    void builtFeatureIsAnImmutableSnapshot() {
        BotFeature.Builder builder = BotFeature.of("card").button("next", context -> HandlerResult.CONSUMED);
        BotFeature built = builder.build();

        builder.button("prev", context -> HandlerResult.CONSUMED);   // build 之后再改 builder

        assertThat(built.buttonHandlers()).hasSize(1);
        assertThatThrownBy(() -> built.buttonHandlers().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidIdAndActionAtBuildTime() {
        assertThatThrownBy(() -> BotFeature.of("card feature"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("featureId");
        assertThatThrownBy(() -> BotFeature.of("card").button("next page", context -> HandlerResult.CONSUMED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }

    @Test
    void factoriesWithoutBuilderWork() {
        List<BotEvent> seen = new ArrayList<>();
        BotEventHandler<C2cMessageCreateEvent> handler =
                BotEventHandler.of(C2cMessageCreateEvent.class, event -> {
                    seen.add(event);
                    return HandlerResult.CONSUMED;
                });
        ButtonHandler button = ButtonHandler.of("next", context -> HandlerResult.IGNORED);

        assertThat(handler.eventType()).isEqualTo(C2cMessageCreateEvent.class);
        assertThat(handler.handle(c2cMessage("你好"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(seen).hasSize(1);
        assertThat(button.action()).isEqualTo("next");
    }

    @Test
    void builtFeatureRoutesButtonClickBackToItsOwnHandler() {
        List<ButtonContext> clicked = new CopyOnWriteArrayList<>();
        BotFeature feature = BotFeature.of("card")
                .button("next", context -> {
                    clicked.add(context);
                    return HandlerResult.CONSUMED;
                })
                .build();
        InteractionRouter router = new InteractionRouter(List.of(feature));

        HandlerResult result = router.handle(click("card:next:p=2", 11, 2));

        assertThat(result).isEqualTo(HandlerResult.CONSUMED);
        assertThat(clicked).hasSize(1);
        assertThat(clicked.getFirst().featureId()).isEqualTo("card");
        assertThat(clicked.getFirst().intState("p", 0)).isEqualTo(2);
    }

    @Test
    void builtFeatureMessageHandlerRunsInTheOrderedChain() {
        List<String> order = new CopyOnWriteArrayList<>();
        BotFeature feature = BotFeature.of("ping")
                .message(C2cMessageCreateEvent.class, event -> {
                    order.add("feature");
                    return HandlerResult.CONSUMED;   // 终止后续处理器
                })
                .build();
        BotEventHandler<C2cMessageCreateEvent> first = BotEventHandler.of(C2cMessageCreateEvent.class, event -> {
            order.add("bean");
            return HandlerResult.IGNORED;
        });
        EventDispatcher dispatcher = new EventDispatcher(concat(first, feature.messageHandlers()));

        dispatcher.dispatch(c2cMessage("/ping"));

        assertThat(order).containsExactly("bean", "feature");
    }

    private static List<BotEventHandler<?>> concat(BotEventHandler<?> first, List<BotEventHandler<?>> rest) {
        List<BotEventHandler<?>> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        return all;
    }

    private static InteractionCreateEvent click(String buttonData, int type, Integer chatType) {
        InteractionResolved resolved = new InteractionResolved(buttonData, "btn-1", null, null, null, null, null, null,
                null, null);
        InteractionCreate payload = new InteractionCreate("EVENT_ID", type, chatType == 2 ? "c2c" : "group", chatType,
                "2026-10-02T00:00:00+08:00", null, null, "USER_OPENID", null, null,
                new InteractionData(type, resolved), 1, "102012345");
        return new InteractionCreateEvent(1, "INTERACTION_CREATE:EVENT_ID", new JsonMapper().readTree("{}"),
                payload);
    }

    private static C2cMessageCreateEvent c2cMessage(String content) {
        MessageAuthor author = new MessageAuthor("ID_1", "用户", false, null, null, null, "USER_OPENID", null, null);
        return new C2cMessageCreateEvent(1, "C2C_MESSAGE_CREATE:ID", new JsonMapper().readTree("{}"),
                new C2cMessage("MSG_1", author, content, null, null, null, null, null, null));
    }
}
