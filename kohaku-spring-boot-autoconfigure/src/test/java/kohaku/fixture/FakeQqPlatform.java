package kohaku.fixture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import love.aira.kohaku.gateway.GatewayOp;
import love.aira.kohaku.gateway.event.BotEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 本地假开放平台夹具：REST 端点 + 可编排行为的 WebSocket 网关。
 *
 * <p>刻意放在 {@code love.aira.kohaku} 之外的包：{@link RestApi} 带 {@code @RestController}，
 * 若位于被 {@code @SpringBootApplication} 扫描的包内，会被测试类路径上的组件扫描捕获并以
 * 无默认构造器的形式实例化，导致上下文启动失败。
 */
public final class FakeQqPlatform {

    private FakeQqPlatform() {
    }

    /** 申请一个空闲端口，供假平台与测试上下文绑定。 */
    public static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 冒充开放平台 REST 接口，提供 AccessToken 与 Gateway 接入点。 */
    @RestController
    public static final class RestApi {

        private final int port;

        public RestApi(int port) {
            this.port = port;
        }

        @PostMapping("/app/getAppAccessToken")
        public Map<String, String> accessToken() {
            return Map.of("access_token", "test-access-token", "expires_in", "7200");
        }

        @GetMapping({"/gateway", "/gateway/bot"})
        public Map<String, String> gatewayUrl() {
            return Map.of("url", "ws://127.0.0.1:" + port + "/websocket");
        }
    }

    /** 冒充 QQ 网关：下发 Hello/ACK/READY/RESUMED，记录客户端上行报文。 */
    public static final class Gateway extends TextWebSocketHandler {

        private final ObjectMapper mapper;
        private final List<JsonNode> received = new CopyOnWriteArrayList<>();
        private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        private final AtomicInteger identifyCount = new AtomicInteger();
        private final AtomicInteger seq = new AtomicInteger();

        private volatile boolean acknowledgeHeartbeats = true;
        private volatile int closeCodeOnConnect;

        public Gateway(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        public void acknowledgeHeartbeats(boolean acknowledge) {
            this.acknowledgeHeartbeats = acknowledge;
        }

        public void closeOnConnectWith(int closeCode) {
            this.closeCodeOnConnect = closeCode;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws IOException {
            sessions.add(session);
            ObjectNode hello = mapper.createObjectNode();
            hello.put("op", GatewayOp.HELLO);
            hello.putObject("d").put("heartbeat_interval", 200);
            send(session, hello);
            int closeCode = closeCodeOnConnect;
            if (closeCode != 0) {
                session.close(new CloseStatus(closeCode, "scripted close"));
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
            JsonNode payload = mapper.readTree(message.getPayload());
            received.add(payload);
            if (!session.isOpen()) {
                return;
            }
            switch (payload.path("op").asInt()) {
                case GatewayOp.IDENTIFY -> send(session, ready(identifyCount.incrementAndGet()));
                case GatewayOp.RESUME ->
                        send(session, dispatch("RESUMED", seq.incrementAndGet(), mapper.createObjectNode()));
                case GatewayOp.HEARTBEAT -> {
                    if (acknowledgeHeartbeats) {
                        ObjectNode ack = mapper.createObjectNode();
                        ack.put("op", GatewayOp.HEARTBEAT_ACK);
                        ack.putNull("d");
                        send(session, ack);
                    }
                }
                default -> {
                }
            }
        }

        public void pushEvent(String type, int eventSeq) throws IOException {
            send(latestSession(), dispatch(type, eventSeq, mapper.createObjectNode()));
        }

        /** 推送一条真实的 AT_MESSAGE_CREATE（频道内 @机器人），用于验证强类型事件反序列化。 */
        public void pushAtMessage(String content, int eventSeq) throws IOException {
            ObjectNode data = mapper.createObjectNode();
            data.put("id", "MSG_" + eventSeq);
            data.put("channel_id", "CH_1");
            data.put("guild_id", "GUILD_1");
            data.put("content", content);
            data.put("timestamp", "2026-10-02T00:00:00+08:00");
            data.put("seq", eventSeq);
            data.putObject("author").put("id", "USER_1").put("username", "tester").put("bot", false);
            send(latestSession(), dispatch("AT_MESSAGE_CREATE", eventSeq, data));
        }

        /** 下发 OpCode 7，要求客户端重连。 */
        public void pushReconnect() throws IOException {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("op", GatewayOp.RECONNECT);
            payload.putNull("d");
            send(latestSession(), payload);
        }

        /** 下发 OpCode 9，{@code resumable} 对应 d 字段（true 表示仍可 Resume）。 */
        public void pushInvalidSession(boolean resumable) throws IOException {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("op", GatewayOp.INVALID_SESSION);
            payload.put("d", resumable);
            send(latestSession(), payload);
        }

        public void close(int statusCode, String reason) throws IOException {
            latestSession().close(new CloseStatus(statusCode, reason));
        }

        public int connectionCount() {
            return sessions.size();
        }

        public List<JsonNode> ofOp(int op) {
            return received.stream().filter(node -> node.path("op").asInt() == op).toList();
        }

        public List<Long> heartbeatSeqs() {
            return ofOp(GatewayOp.HEARTBEAT).stream()
                    .map(node -> node.path("d"))
                    .filter(JsonNode::isNumber)
                    .map(JsonNode::asLong)
                    .toList();
        }

        private ObjectNode ready(int identifyNumber) {
            ObjectNode data = mapper.createObjectNode();
            data.put("version", 1);
            data.put("session_id", "sess-test-" + identifyNumber);
            data.putObject("user")
                    .put("id", "6158788878435714165")
                    .put("username", "kohaku-test")
                    .put("bot", true);
            data.putArray("shard").add(0).add(1);
            return dispatch("READY", seq.incrementAndGet(), data);
        }

        private ObjectNode dispatch(String type, int eventSeq, ObjectNode data) {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("op", GatewayOp.DISPATCH);
            payload.put("s", eventSeq);
            payload.put("t", type);
            payload.set("d", data);
            return payload;
        }

        private void send(WebSocketSession session, ObjectNode payload) throws IOException {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
            }
        }

        private WebSocketSession latestSession() {
            if (sessions.isEmpty()) {
                throw new IllegalStateException("gateway has no connection yet");
            }
            return sessions.get(sessions.size() - 1);
        }
    }

    /** 收集客户端发布的所有网关事件。 */
    public static final class EventRecorder {

        private final List<BotEvent> events = Collections.synchronizedList(new ArrayList<>());

        @EventListener
        public void onBotEvent(BotEvent event) {
            events.add(event);
        }

        public <T extends BotEvent> List<T> ofType(Class<T> type) {
            synchronized (events) {
                return events.stream().filter(type::isInstance).map(type::cast).toList();
            }
        }

        public <T extends BotEvent> T last(Class<T> type) {
            List<T> matching = ofType(type);
            return matching.get(matching.size() - 1);
        }
    }
}
