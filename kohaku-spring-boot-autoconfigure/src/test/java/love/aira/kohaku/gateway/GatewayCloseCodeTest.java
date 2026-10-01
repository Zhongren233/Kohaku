package love.aira.kohaku.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GatewayCloseCodeTest {

    @ParameterizedTest
    @CsvSource({
            // 不可重试：无效 opcode/payload/shard/version/intent、intent 无权限、已下架、已封禁
            "4001,FATAL",
            "4002,FATAL",
            "4010,FATAL",
            "4011,FATAL",
            "4012,FATAL",
            "4013,FATAL",
            "4014,FATAL",
            "4914,FATAL",
            "4915,FATAL",
            // 清 session 重新 Identify：seq 错误、无效 session id
            "4006,IDENTIFY",
            "4007,IDENTIFY",
            // 保留 session 并发起 Resume：发送过快、连接过期、异常断开
            "4008,RESUME",
            "4009,RESUME",
            "-1,RESUME",
            "1000,RESUME",
            "1006,RESUME"
    })
    void mapsDocumentedCloseCodes(int code, ReconnectAction expected) {
        assertThat(GatewayCloseCode.actionFor(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"4900", "4901", "4913"})
    void mapsInternalErrorsToIdentify(int code) {
        assertThat(GatewayCloseCode.actionFor(code)).isEqualTo(ReconnectAction.IDENTIFY);
    }

    @Test
    void mapsUnknownServerErrorsToIdentify() {
        assertThat(GatewayCloseCode.actionFor(4005)).isEqualTo(ReconnectAction.IDENTIFY);
        assertThat(GatewayCloseCode.actionFor(4999)).isEqualTo(ReconnectAction.IDENTIFY);
    }
}
