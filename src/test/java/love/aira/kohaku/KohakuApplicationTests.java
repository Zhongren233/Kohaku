package love.aira.kohaku;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "kohaku.qq.auto-start=false",
        "kohaku.qq.app-id=test-app",
        "kohaku.qq.app-secret=test-secret"})
class KohakuApplicationTests {

    @Test
    void contextLoads() {
    }

}
