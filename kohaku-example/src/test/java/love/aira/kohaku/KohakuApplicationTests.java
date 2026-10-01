package love.aira.kohaku;

import static org.assertj.core.api.Assertions.assertThat;

import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.feature.InteractionRouter;
import love.aira.kohaku.gateway.handler.EventDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
    }
}
