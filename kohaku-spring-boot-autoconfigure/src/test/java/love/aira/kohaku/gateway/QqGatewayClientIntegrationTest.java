package love.aira.kohaku.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import kohaku.fixture.FakeQqPlatform;
import love.aira.kohaku.gateway.event.BotDispatchEvent;
import love.aira.kohaku.gateway.event.BotReadyEvent;
import love.aira.kohaku.gateway.event.BotResumedEvent;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 端到端走完整网关时序：换 token → 取接入点 → Hello → Identify → READY → 心跳携带最新 s →
 * 4009 断开后 Resume → OpCode 9(d=false) 清 session 重新 Identify → OpCode 9(d=true) 保留 session Resume →
 * OpCode 7 服务端要求重连则 Resume → 心跳无 ACK 判僵尸连接仍 Resume → 4006 断开后重新 Identify。
 *
 * <p>全程只访问本地假网关（Tomcat WebSocket），并顺带钉住 REST 端点路径：假平台只注册 {@code GET /gateway}，
 * 路径写错会直接 404。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class QqGatewayClientIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int PORT = FakeQqPlatform.freePort();

    @DynamicPropertySource
    static void qqProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("kohaku.qq.api-base-url", () -> "http://127.0.0.1:" + PORT);
        registry.add("kohaku.qq.token-url", () -> "http://127.0.0.1:" + PORT + "/app/getAppAccessToken");
        registry.add("kohaku.qq.app-id", () -> "test-app");
        registry.add("kohaku.qq.app-secret", () -> "test-secret");
        registry.add("kohaku.qq.reconnect-initial-delay", () -> "20ms");
        registry.add("kohaku.qq.reconnect-max-delay", () -> "100ms");
        // 不依赖 application.yaml：显式指定多选枚举，顺带覆盖“枚举 → 位掩码 → Identify 载荷”整条链路
        registry.add("kohaku.qq.intents[0]", () -> "GUILDS");
        registry.add("kohaku.qq.intents[1]", () -> "PUBLIC_GUILD_MESSAGES");
    }

    @Autowired
    private FakeQqPlatform.Gateway gateway;

    @Autowired
    private FakeQqPlatform.EventRecorder recorder;

    @Test
    void followsFullGatewayLifecycle() throws Exception {
        // 1. Identify 鉴权 → READY
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(recorder.ofType(BotReadyEvent.class)).hasSize(1));
        BotReadyEvent ready = recorder.last(BotReadyEvent.class);
        assertThat(ready.sessionId()).isEqualTo("sess-test-1");
        assertThat(ready.user().username()).isEqualTo("kohaku-test");
        assertThat(ready.shard()).containsExactly(0, 1);

        JsonNode identify = gateway.ofOp(GatewayOp.IDENTIFY).getFirst();
        assertThat(identify.path("d").path("token").stringValue()).isEqualTo("QQBot test-access-token");
        assertThat(identify.path("d").path("intents").asInt()).isEqualTo((1 << 0) | (1 << 30));
        assertThat(identify.path("d").path("shard").get(0).asInt()).isZero();
        assertThat(identify.path("d").path("shard").get(1).asInt()).isEqualTo(1);

        // 2. 事件序号推进后，心跳必须携带最新的 s
        gateway.pushEvent("AT_MESSAGE_CREATE", 42);
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(recorder.ofType(BotDispatchEvent.class))
                .anySatisfy(event -> assertThat(event.seq()).isEqualTo(42)));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(gateway.heartbeatSeqs()).contains(42L));

        // 3. 连接过期（4009）：保留 session 并补发遗漏事件
        gateway.close(4009, "connection expired");
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(gateway.ofOp(GatewayOp.RESUME)).hasSize(1));
        JsonNode resume = gateway.ofOp(GatewayOp.RESUME).getFirst();
        assertThat(resume.path("d").path("session_id").stringValue()).isEqualTo("sess-test-1");
        assertThat(resume.path("d").path("seq").asLong()).isEqualTo(42L);
        assertThat(resume.path("d").path("token").stringValue()).isEqualTo("QQBot test-access-token");
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(recorder.ofType(BotResumedEvent.class)).hasSize(1));

        // 4. OpCode 9 且 d=false：session 不可续用，丢弃后重新 Identify
        gateway.pushInvalidSession(false);
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(gateway.ofOp(GatewayOp.IDENTIFY)).hasSize(2));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(recorder.ofType(BotReadyEvent.class)).hasSize(2));
        assertThat(recorder.last(BotReadyEvent.class).sessionId()).isEqualTo("sess-test-2");

        // 5. OpCode 9 且 d=true：session 仍可使用，保留后 Resume
        gateway.pushInvalidSession(true);
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(gateway.ofOp(GatewayOp.RESUME)).hasSize(2));
        assertThat(gateway.ofOp(GatewayOp.RESUME).get(1).path("d").path("session_id").stringValue())
                .isEqualTo("sess-test-2");

        // 6. OpCode 7：服务端要求重连，保留 session Resume
        gateway.pushReconnect();
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(gateway.ofOp(GatewayOp.RESUME)).hasSize(3));

        // 7. 心跳长时间无 ACK：判定僵尸连接主动重连，且仍保留 session
        gateway.acknowledgeHeartbeats(false);
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(gateway.ofOp(GatewayOp.RESUME)).hasSizeGreaterThan(3));
        gateway.acknowledgeHeartbeats(true);

        // 8. session 失效（4006）：丢弃 session 重新 Identify，且不能复用旧 session
        gateway.close(4006, "invalid session");
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(gateway.ofOp(GatewayOp.IDENTIFY)).hasSize(3));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(recorder.ofType(BotReadyEvent.class)).hasSize(3));
        assertThat(recorder.last(BotReadyEvent.class).sessionId()).isEqualTo("sess-test-3");
    }

    @TestConfiguration
    @EnableWebSocket
    static class FakeQqPlatformConfiguration implements WebSocketConfigurer {

        private final FakeQqPlatform.Gateway gateway;

        FakeQqPlatformConfiguration(ObjectMapper mapper) {
            this.gateway = new FakeQqPlatform.Gateway(mapper);
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
