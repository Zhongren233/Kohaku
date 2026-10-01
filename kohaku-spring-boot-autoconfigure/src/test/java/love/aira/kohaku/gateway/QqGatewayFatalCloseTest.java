package love.aira.kohaku.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import kohaku.fixture.FakeQqPlatform;
import love.aira.kohaku.gateway.event.BotReadyEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.ObjectMapper;

/**
 * 网关以 4914（机器人已下架）拒绝连接时，客户端必须停止且不再重连——这是文档中明确不可重试的错误码。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class QqGatewayFatalCloseTest {

    private static final int PORT = FakeQqPlatform.freePort();
    private static final int FATAL_CODE = 4914;

    @DynamicPropertySource
    static void qqProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("kohaku.qq.api-base-url", () -> "http://127.0.0.1:" + PORT);
        registry.add("kohaku.qq.token-url", () -> "http://127.0.0.1:" + PORT + "/app/getAppAccessToken");
        registry.add("kohaku.qq.app-id", () -> "test-app");
        registry.add("kohaku.qq.app-secret", () -> "test-secret");
        registry.add("kohaku.qq.reconnect-initial-delay", () -> "20ms");
        registry.add("kohaku.qq.reconnect-max-delay", () -> "100ms");
    }

    @Autowired
    private QqGatewayClient client;

    @Autowired
    private FakeQqPlatform.Gateway gateway;

    @Autowired
    private FakeQqPlatform.EventRecorder recorder;

    @Test
    void stopsWithoutReconnectingOnNonRetryableCloseCode() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(client.isRunning()).isFalse());

        int connections = gateway.connectionCount();
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .until(() -> !client.isRunning() && gateway.connectionCount() == connections);

        assertThat(gateway.ofOp(GatewayOp.IDENTIFY)).hasSizeLessThanOrEqualTo(1);
        assertThat(recorder.ofType(BotReadyEvent.class)).isEmpty();
    }

    @TestConfiguration
    @EnableWebSocket
    static class FatalClosePlatformConfiguration implements WebSocketConfigurer {

        private final FakeQqPlatform.Gateway gateway;

        FatalClosePlatformConfiguration(ObjectMapper mapper) {
            this.gateway = new FakeQqPlatform.Gateway(mapper);
            this.gateway.closeOnConnectWith(FATAL_CODE);
        }

        @Bean
        FakeQqPlatform.Gateway fakeGateway() {
            return gateway;
        }

        @Bean
        FakeQqPlatform.EventRecorder eventRecorder() {
            return new FakeQqPlatform.EventRecorder();
        }

        @Bean
        FakeQqPlatform.RestApi fakeQqRestApi() {
            return new FakeQqPlatform.RestApi(PORT);
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(gateway, "/websocket");
        }
    }
}
