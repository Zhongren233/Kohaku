package love.aira.kohaku;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void contextLoads() {
    }

    /** 示例项目里的两个处理器都被自动收集，且按 @Order 排序。 */
    @Test
    void registersExampleHandlersInOrder() {
        assertThat(dispatcher.handlers())
                .extracting(handler -> handler.getClass().getSimpleName())
                .containsExactly("EchoC2cHandler", "EchoGroupHandler");
    }
}
