package love.aira.kohaku.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import love.aira.kohaku.api.AccessTokenProvider;
import love.aira.kohaku.api.QqGatewayApi;
import love.aira.kohaku.config.KohakuConfig;
import love.aira.kohaku.gateway.event.BotDispatchEvent;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.BotReadyEvent;
import love.aira.kohaku.gateway.event.BotResumedEvent;
import love.aira.kohaku.gateway.event.BotUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * QQ 机器人 Gateway 长连接客户端。
 *
 * <p>完整实现开放平台的连接时序：
 * <ol>
 *   <li>连接 WSS 后接收 OpCode 10 Hello，取得 {@code heartbeat_interval}；</li>
 *   <li>发送 OpCode 2 Identify（首次）或 OpCode 6 Resume（持有 session 时）完成鉴权；</li>
 *   <li>按 Hello 下发的周期发送 OpCode 1 心跳，{@code d} 为最近收到的 {@code s}（首次为 null），
 *       期待 OpCode 11 ACK；若下一次心跳时仍未收到 ACK，判定为僵尸连接并强制重连；</li>
 *   <li>断线后按 {@link GatewayCloseCode} 决定 Resume（携带 {@code session_id} 与 {@code seq}，
 *       网关补发遗漏事件）或丢弃 session 重新 Identify，并做指数退避重连；</li>
 *   <li>不可重试的错误码（机器人封禁/下架、无效 opcode 等）直接停止。</li>
 * </ol>
 *
 * <p>所有网络回调由单线程执行器串行化，心跳由独立的单线程调度器驱动；事件通过构造时传入的
 * {@code Consumer<BotEvent>} 回调派发，因此本类不依赖任何框架：非 Spring 宿主直接 new 出来用即可，
 * Spring 宿主由 {@code kohaku-spring-boot-autoconfigure} 把回调接到容器事件、
 * 并用 {@code SmartLifecycle} 适配 {@link #start()} / {@link #stop()}。
 */
public class QqGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(QqGatewayClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration HELLO_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);
    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 45_000;
    private static final long MAX_BACKOFF_SHIFT = 16;
    private static final Pattern AUTH_TOKEN = Pattern.compile("(\"token\"\\s*:\\s*\")[^\"]*(\")");

    private final KohakuConfig config;
    private final QqGatewayApi gatewayApi;
    private final AccessTokenProvider tokens;
    private final ObjectMapper mapper;
    private final Consumer<BotEvent> listener;
    private final GatewayEventDecoder eventDecoder;

    private final Object lock = new Object();

    private volatile boolean running;
    private HttpClient httpClient;
    private ExecutorService callbackExecutor;
    private ScheduledExecutorService scheduler;

    /** 当前活跃连接，同时作为丢弃陈旧回调的身份标识。 */
    private Connection current;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> helloWatchdog;
    private String sessionId;
    private Long lastSeq;
    private String gatewayUrl;
    private boolean awaitingHeartbeatAck;
    private long heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS;
    private int reconnectAttempt;

    public QqGatewayClient(KohakuConfig config, QqGatewayApi gatewayApi, AccessTokenProvider tokens,
                           ObjectMapper mapper, Consumer<BotEvent> listener) {
        this.config = config;
        this.gatewayApi = gatewayApi;
        this.tokens = tokens;
        this.mapper = mapper;
        this.listener = listener;
        this.eventDecoder = new GatewayEventDecoder(mapper);
    }

    public void start() {
        synchronized (lock) {
            if (running) {
                return;
            }
            running = true;
            callbackExecutor = Executors.newSingleThreadExecutor(r -> clientThread(r, "qq-gateway-io"));
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> clientThread(r, "qq-gateway-scheduler"));
            httpClient = HttpClient.newBuilder()
                    .executor(callbackExecutor)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
        }
        log.info("QQ gateway client starting (intents={} mask={}, shard=[{},{}])",
                config.intents(), config.intentsMask(), config.shardIndex(), config.shardTotal());
        connect();
    }

    public void stop() {
        Connection connection;
        synchronized (lock) {
            connection = current;
            current = null;
            running = false;
            cancel(heartbeatTask);
            cancel(helloWatchdog);
            heartbeatTask = null;
            helloWatchdog = null;
        }
        if (connection != null) {
            connection.closeGracefully();
        }
        shutdownExecutors();
        log.info("QQ gateway client stopped");
    }

    public boolean isRunning() {
        return running;
    }

    // ------------------------------------------------------------------ 连接建立

    private void connect() {
        if (!running) {
            return;
        }
        boolean resume;
        String url;
        synchronized (lock) {
            resume = resumable();
            url = gatewayUrl;
        }
        try {
            if (url == null) {
                url = gatewayApi.url();
                synchronized (lock) {
                    gatewayUrl = url;
                }
            }
        } catch (RuntimeException e) {
            log.warn("cannot resolve gateway url: {}", e.toString());
            scheduleReconnect();
            return;
        }

        Connection connection = new Connection(resume);
        synchronized (lock) {
            if (!running) {
                return;
            }
            current = connection;
            helloWatchdog = scheduler.schedule(() -> onHelloTimeout(connection), HELLO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
        log.info("connecting to gateway (resume={})", resume);
        try {
            httpClient.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(URI.create(url), connection)
                    .whenComplete((webSocket, error) -> {
                        if (error != null && connection.terminate()) {
                            log.warn("gateway connection failed: {}", error.toString());
                            handleClosed(connection, GatewayCloseCode.ABNORMAL, error.toString());
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("cannot open gateway connection: {}", e.toString());
            if (connection.terminate()) {
                handleClosed(connection, GatewayCloseCode.ABNORMAL, e.toString());
            }
        }
    }

    private void onHelloTimeout(Connection connection) {
        synchronized (lock) {
            if (connection != current || connection.helloReceived) {
                return;
            }
        }
        log.warn("no HELLO received within {} ms, reconnecting", HELLO_TIMEOUT.toMillis());
        reconnect(connection, "hello timeout");
    }

    // ------------------------------------------------------------------ 报文处理

    private void handleText(Connection connection, String text) {
        GatewayPayload payload;
        try {
            payload = mapper.readValue(text, GatewayPayload.class);
        } catch (JacksonException e) {
            log.warn("cannot parse gateway payload {}: {}", abbreviate(text), e.toString());
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("gateway <- {}", redact(text));
        }
        switch (payload.op()) {
            case GatewayOp.HELLO -> handleHello(connection, payload);
            case GatewayOp.HEARTBEAT_ACK -> handleHeartbeatAck(connection);
            case GatewayOp.RECONNECT -> handleReconnect(connection);
            case GatewayOp.INVALID_SESSION -> handleInvalidSession(connection, payload);
            case GatewayOp.DISPATCH -> handleDispatch(payload);
            default -> log.warn("ignoring unsupported gateway opcode {}: {}", payload.op(), abbreviate(redact(text)));
        }
    }

    /** 服务端要求重连（OpCode 7）：放弃当前连接，保留 session 走 Resume。 */
    private void handleReconnect(Connection connection) {
        log.info("gateway requested reconnect (op {})", GatewayOp.RECONNECT);
        reconnect(connection, "server requested reconnect");
    }

    /**
     * 服务端判定 session 无效（OpCode 9）：{@code d} 为 true 时仍可 Resume，
     * 为 false 表示不能续用 session，须丢弃后重新 Identify。
     */
    private void handleInvalidSession(Connection connection, GatewayPayload payload) {
        boolean resumable = payload.d() != null && payload.d().booleanValue(false);
        if (!resumable) {
            synchronized (lock) {
                sessionId = null;
                lastSeq = null;
            }
        }
        log.warn("gateway invalidated the session (resumable={}), reconnecting", resumable);
        reconnect(connection, "invalid session");
    }

    private void handleHello(Connection connection, GatewayPayload payload) {
        synchronized (lock) {
            if (connection != current) {
                return;
            }
            connection.helloReceived = true;
            long interval = payload.d() == null ? 0 : payload.d().path("heartbeat_interval").asLong(0);
            heartbeatIntervalMs = interval > 0 ? interval : DEFAULT_HEARTBEAT_INTERVAL_MS;
            cancel(helloWatchdog);
            helloWatchdog = null;
            // 首包在周期内随机抖动，避免集群同时发送心跳。
            heartbeatTask = scheduler.schedule(() -> heartbeatTick(connection),
                    ThreadLocalRandom.current().nextLong(heartbeatIntervalMs + 1), TimeUnit.MILLISECONDS);
            log.info("gateway HELLO received (heartbeat {} ms), authenticating via {}",
                    heartbeatIntervalMs, connection.resume ? "RESUME" : "IDENTIFY");
            send(connection, authPayload(connection));
        }
    }

    private void heartbeatTick(Connection connection) {
        synchronized (lock) {
            if (connection != current || !running) {
                return;
            }
            if (awaitingHeartbeatAck) {
                log.warn("no heartbeat ACK within {} ms, treating connection as zombie", heartbeatIntervalMs);
                awaitingHeartbeatAck = false;
                reconnect(connection, "heartbeat ACK timeout");
                return;
            }
            ObjectNode payload = mapper.createObjectNode();
            payload.put("op", GatewayOp.HEARTBEAT);
            if (lastSeq == null) {
                payload.putNull("d");
            } else {
                payload.put("d", lastSeq);
            }
            awaitingHeartbeatAck = true;
            heartbeatTask = scheduler.schedule(() -> heartbeatTick(connection), heartbeatIntervalMs, TimeUnit.MILLISECONDS);
            send(connection, payload);
        }
    }

    private void handleHeartbeatAck(Connection connection) {
        synchronized (lock) {
            if (connection != current) {
                return;
            }
            awaitingHeartbeatAck = false;
        }
        log.debug("gateway heartbeat acknowledged");
    }

    private void handleDispatch(GatewayPayload payload) {
        JsonNode data = payload.d() == null ? NullNode.getInstance() : payload.d();
        long seq = payload.s() == null ? 0 : payload.s();
        String type = payload.t();
        synchronized (lock) {
            if (payload.s() != null) {
                lastSeq = payload.s();
            }
        }
        if (type == null) {
            log.warn("dispatch payload without event type: op={} s={}", payload.op(), payload.s());
            return;
        }
        switch (type) {
            case "READY" -> {
                BotReadyEvent event = new BotReadyEvent(seq, data.path("session_id").stringValue(null),
                        new BotUser(data.path("user").path("id").stringValue(null),
                                data.path("user").path("username").stringValue(null)),
                        readShard(data.path("shard")));
                synchronized (lock) {
                    sessionId = event.sessionId();
                    reconnectAttempt = 0;
                }
                log.info("gateway READY: session={} bot={}({}) shard={}",
                        event.sessionId(), event.user().username(), event.user().id(), event.shard());
                publish(event);
            }
            case "RESUMED" -> {
                synchronized (lock) {
                    reconnectAttempt = 0;
                    awaitingHeartbeatAck = false;
                }
                log.info("gateway RESUMED, missed events have been replayed");
                publish(new BotResumedEvent(seq));
            }
            default -> {
                String eventId = payload.id();
                BotDispatchEvent typed = eventDecoder.decode(seq, type, eventId, data);
                publish(typed != null ? typed : new BotDispatchEvent(seq, type, eventId, data));
            }
        }
    }

    // ------------------------------------------------------------------ 断开与重连

    private void handleClosed(Connection connection, int code, String reason) {
        synchronized (lock) {
            if (connection != current) {
                return;
            }
            current = null;
            cancel(heartbeatTask);
            cancel(helloWatchdog);
            heartbeatTask = null;
            helloWatchdog = null;
            awaitingHeartbeatAck = false;
        }
        ReconnectAction action = GatewayCloseCode.actionFor(code);
        log.info("gateway connection closed (code={}, reason={}) -> {}", code, reason, action);
        switch (action) {
            case FATAL -> {
                log.error("gateway connection closed with non-retryable code {}, stopping client: {}",
                        code, reason);
                halt();
            }
            case IDENTIFY -> {
                synchronized (lock) {
                    sessionId = null;
                    lastSeq = null;
                }
                scheduleReconnect();
            }
            case RESUME -> scheduleReconnect();
        }
    }

    /** 主动放弃当前连接（心跳超时、Hello 超时），保留 session 以便 Resume。 */
    private void reconnect(Connection connection, String reason) {
        synchronized (lock) {
            if (connection != current) {
                return;
            }
            current = null;
            cancel(heartbeatTask);
            cancel(helloWatchdog);
            heartbeatTask = null;
            helloWatchdog = null;
            awaitingHeartbeatAck = false;
        }
        log.info("dropping gateway connection ({}), resumable={}", reason, resumable());
        connection.abort();
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        long delay;
        synchronized (lock) {
            if (!running) {
                return;
            }
            delay = nextBackoffMillis();
        }
        log.info("reconnecting to gateway in {} ms", delay);
        try {
            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("reconnect skipped, scheduler is shut down");
        }
    }

    private long nextBackoffMillis() {
        long base = Math.max(1, config.reconnectInitialDelay().toMillis());
        long max = Math.max(base, config.reconnectMaxDelay().toMillis());
        long ceiling = Math.min(max, base << Math.min(reconnectAttempt, MAX_BACKOFF_SHIFT));
        reconnectAttempt++;
        long spread = Math.max(1, ceiling / 5);
        return Math.min(max, ceiling - spread + ThreadLocalRandom.current().nextLong(2 * spread));
    }

    private boolean resumable() {
        return sessionId != null && lastSeq != null;
    }

    private void halt() {
        synchronized (lock) {
            running = false;
            cancel(heartbeatTask);
            cancel(helloWatchdog);
            heartbeatTask = null;
            helloWatchdog = null;
            current = null;
        }
        shutdownExecutors();
    }

    // ------------------------------------------------------------------ 下行报文

    private ObjectNode authPayload(Connection connection) {
        ObjectNode data = mapper.createObjectNode();
        data.put("token", tokens.authorization());
        if (connection.resume) {
            data.put("session_id", sessionId);
            data.put("seq", lastSeq);
        } else {
            data.put("intents", config.intentsMask());
            ArrayNode shard = data.putArray("shard");
            shard.add(config.shardIndex());
            shard.add(config.shardTotal());
            ObjectNode client = data.putObject("properties");
            client.put("$os", System.getProperty("os.name", "unknown"));
            client.put("$browser", config.clientName());
            client.put("$device", config.clientName());
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.put("op", connection.resume ? GatewayOp.RESUME : GatewayOp.IDENTIFY);
        payload.set("d", data);
        return payload;
    }

    private void send(Connection connection, ObjectNode payload) {
        WebSocket socket = connection.webSocket;
        if (socket == null) {
            log.warn("cannot send op {}: connection is not open", payload.path("op").asInt());
            return;
        }
        String text = mapper.writeValueAsString(payload);
        if (log.isDebugEnabled()) {
            log.debug("gateway -> {}", redact(text));
        }
        socket.sendText(text, true).whenComplete((sent, error) -> {
            if (error != null) {
                log.warn("failed to send gateway payload: {}", error.toString());
            }
        });
    }

    // ------------------------------------------------------------------ 辅助

    private void publish(BotEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException e) {
            // 业务监听器异常不得影响长连接读写循环。
            log.error("gateway event listener failed for {}", event.getClass().getSimpleName(), e);
        }
    }

    private static List<Integer> readShard(JsonNode shard) {
        if (shard == null || !shard.isArray()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>(shard.size());
        shard.forEach(value -> values.add(value.asInt()));
        return values;
    }

    private void shutdownExecutors() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (callbackExecutor != null) {
            callbackExecutor.shutdown();
            callbackExecutor = null;
        }
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 非守护线程：应用不再内嵌 Web 容器，由网关连接与心跳线程维持进程存活；
     * {@link #stop()} 关闭执行器后进程即可正常退出。
     */
    private static Thread clientThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(false);
        return thread;
    }

    private static String abbreviate(String text) {
        return text.length() <= 256 ? text : text.substring(0, 256) + "...";
    }

    /** 日志脱敏：Identify/Resume 报文携带 AccessToken，不得落盘。 */
    private static String redact(String json) {
        return AUTH_TOKEN.matcher(json).replaceAll("$1***$2");
    }

    /**
     * 单条 WebSocket 连接。持有自身 socket 引用，避免与后续连接互相干扰；
     * {@link #terminate()} 保证 onError / onClose / 连接失败三条路径只触发一次关闭处理。
     */
    private final class Connection implements WebSocket.Listener {

        private final boolean resume;
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final StringBuilder buffer = new StringBuilder();
        private volatile WebSocket webSocket;
        private volatile boolean helloReceived;

        private Connection(boolean resume) {
            this.resume = resume;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.webSocket = webSocket;
            log.debug("gateway websocket established, awaiting HELLO");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                try {
                    handleText(this, text);
                } catch (RuntimeException e) {
                    log.error("failed to handle gateway payload: {}", abbreviate(text), e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (terminate()) {
                handleClosed(this, statusCode, reason);
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("gateway websocket error: {}", error.toString());
            webSocket.abort();
            if (terminate()) {
                handleClosed(this, GatewayCloseCode.ABNORMAL, error.toString());
            }
        }

        private boolean terminate() {
            return terminated.compareAndSet(false, true);
        }

        private void abort() {
            WebSocket socket = webSocket;
            if (socket != null) {
                socket.abort();
            }
        }

        private void closeGracefully() {
            WebSocket socket = webSocket;
            if (socket == null) {
                return;
            }
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").get(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                socket.abort();
            } catch (Exception e) {
                socket.abort();
            }
        }
    }
}
