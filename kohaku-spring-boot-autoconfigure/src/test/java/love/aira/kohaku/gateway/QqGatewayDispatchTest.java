package love.aira.kohaku.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kohaku.fixture.FakeQqPlatform;
import love.aira.kohaku.gateway.event.AtMessageCreateEvent;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
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
 * 业务 handler 变慢不得把健康连接误判为僵尸：handler 在专用派发线程上执行、不占用网络读循环，
 * 因此即使单个 handler 阻塞超过多个心跳周期，HEARTBEAT_ACK 仍能被及时处理，连接保持不重连。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class QqGatewayDispatchTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int PORT = FakeQqPlatform.freePort();
    /** 假网关下发的 heartbeat_interval 是 200ms（见 FakeQqPlatform），阻塞必须明显超过它。 */
    private static final long HANDLER_BLOCK_MILLIS = 700;

    @DynamicPropertySource
    static void qqProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("kohaku.qq.api-base-url", () -> "http://127.0.0.1:" + PORT);
        registry.add("kohaku.qq.token-url", () -> "http://127.0.0.1:" + PORT + "/app/getAppAccessToken");
        registry.add("kohaku.qq.app-id", () -> "test-app");
        registry.add("kohaku.qq.app-secret", () -> "test-secret");
        registry.add("kohaku.qq.reconnect-initial-delay", () -> "20ms");
        registry.add("kohaku.qq.reconnect-max-delay", () -> "100ms");
        registry.add("kohaku.qq.intents[0]", () -> "PUBLIC_GUILD_MESSAGES");
    }

    @Autowired
    private FakeQqPlatform.Gateway gateway;

    @Autowired
    private SlowHandler slowHandler;

    @Test
    void slowHandlerDoesNotCauseZombieReconnect() throws Exception {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(gateway.ofOp(GatewayOp.IDENTIFY)).hasSize(1));

        gateway.pushAtMessage("slow", 7);

        assertThat(slowHandler.finished.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        // handler 不在网络读循环线程上执行
        assertThat(slowHandler.threadName.get()).startsWith("qq-gateway-dispatch");

        // 心跳携带最新 seq、持续推进，说明 ACK 被及时处理；且全程没有重连。
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(gateway.heartbeatSeqs()).contains(7L));
        assertThat(gateway.connectionCount()).isEqualTo(1);
    }

    @TestConfiguration
    @EnableWebSocket
    static class Fixture implements WebSocketConfigurer {

        private final FakeQqPlatform.Gateway gateway;

        Fixture(ObjectMapper mapper) {
            this.gateway = new FakeQqPlatform.Gateway(mapper);
        }

        @Bean
        FakeQqPlatform.Gateway fakeGateway() {
            return gateway;
        }

        @Bean
        FakeQqPlatform.RestApi fakeQqRestApi() {
            return new FakeQqPlatform.RestApi(PORT);
        }

        @Bean
        SlowHandler slowHandler() {
            return new SlowHandler();
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(gateway, "/websocket");
        }
    }

    /** 收到一条消息就阻塞，模拟业务里的慢调用（DB / LLM / 慢 REST）。 */
    static class SlowHandler implements BotEventHandler<AtMessageCreateEvent> {

        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<String> threadName = new AtomicReference<>();

        @Override
        public Class<AtMessageCreateEvent> eventType() {
            return AtMessageCreateEvent.class;
        }

        @Override
        public HandlerResult handle(AtMessageCreateEvent event) {
            threadName.set(Thread.currentThread().getName());
            try {
                Thread.sleep(HANDLER_BLOCK_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished.countDown();
            return HandlerResult.IGNORED;
        }
    }
}
