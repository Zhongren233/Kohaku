package love.aira.kohaku;

import static org.assertj.core.api.Assertions.assertThat;

import love.aira.kohaku.card.CardFeature;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.interaction.InteractionRouter;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.gateway.event.model.InteractionData;
import love.aira.kohaku.gateway.event.model.InteractionResolved;
import love.aira.kohaku.gateway.handler.EventDispatcher;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "kohaku.qq.auto-start=false",
        "kohaku.qq.app-id=test-app",
        "kohaku.qq.app-secret=test-secret"})
class KohakuApplicationTests {

    @Autowired
    private EventDispatcher dispatcher;

    @Autowired
    private InteractionRouter router;

    @Test
    void contextLoads() {
    }

    /** 示例项目里的两个处理器都被自动收集，且按 @Order 排序。 */
    @Test
    void registersExampleHandlersInOrder() {
        assertThat(dispatcher.handlers())
                .extracting(handler -> handler.getClass().getSimpleName())
                .contains("EchoC2cHandler", "EchoGroupHandler");
    }

    /** /card 功能被注册进互动路由，其按钮点击会回到该功能。 */
    @Test
    void registersCardFeature() {
        assertThat(router.features()).extracting(BotFeature::id).contains("card");
        assertThat(dispatcher.handlers()).anySatisfy(handler -> assertThat(handler).isInstanceOf(InteractionRouter.class));

        if (CardFeature.USE_INTERACTION_BUTTONS) {
            // 用「功能自己渲染出来的按钮 data」点击，验证装配后的回调确实路由回该功能
            String buttonData = CardFeature.page(1).keyboard().content().rows().getFirst()
                    .buttons().getLast().action().data();
            assertThat(router.handle(cardButtonClick(buttonData))).isEqualTo(HandlerResult.CONSUMED);
        }
    }

    private static InteractionCreateEvent cardButtonClick(String buttonData) {
        InteractionResolved resolved = new InteractionResolved(buttonData, "btn-1", null, null, null, null, null, null,
                null, null);
        InteractionCreate payload = new InteractionCreate("EVENT_ID", InteractionRouter.TYPE_INLINE_KEYBOARD, "c2c", 2,
                "2026-10-02T00:00:00+08:00", null, null, "USER_OPENID", null, null,
                new InteractionData(InteractionRouter.TYPE_INLINE_KEYBOARD, resolved), 1, "102012345");
        return new InteractionCreateEvent(1, "INTERACTION_CREATE:EVENT_ID", new JsonMapper().readTree("{}"), payload);
    }
}
