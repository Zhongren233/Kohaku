package love.aira.kohaku.gateway;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单条 WebSocket 连接：帧缓冲、socket 生命周期，以及「关闭只处理一次」的语义。
 *
 * <p>持有自身 socket 引用，避免与后续连接互相干扰；{@link #terminate()} 保证
 * onError / onClose / 连接失败三条路径只触发一次 {@link Events#onClosed}。
 * 报文处理与连接状态由 {@link QqGatewayClient} 负责，本类只做搬运。
 */
final class GatewayConnection implements WebSocket.Listener {

    private static final Logger log = LoggerFactory.getLogger(GatewayConnection.class);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);

    /** 连接事件回调，由 {@link QqGatewayClient} 实现。 */
    interface Events {

        /** 收到一条完整文本报文（分片已拼接）。 */
        void onMessage(GatewayConnection connection, String text);

        /** 连接结束（对端关闭、出错或连接失败），只会回调一次。 */
        void onClosed(GatewayConnection connection, int code, String reason);
    }

    private final boolean resume;
    private final Events events;
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final StringBuilder buffer = new StringBuilder();

    private volatile WebSocket webSocket;
    private volatile boolean helloReceived;

    GatewayConnection(boolean resume, Events events) {
        this.resume = resume;
        this.events = events;
    }

    /** 本次连接是否走 Resume（否则 Identify）。 */
    boolean resume() {
        return resume;
    }

    boolean helloReceived() {
        return helloReceived;
    }

    void markHelloReceived() {
        helloReceived = true;
    }

    WebSocket socket() {
        return webSocket;
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
            events.onMessage(this, text);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (terminate()) {
            events.onClosed(this, statusCode, reason);
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.warn("gateway websocket error: {}", error.toString());
        webSocket.abort();
        if (terminate()) {
            events.onClosed(this, GatewayCloseCode.ABNORMAL, error.toString());
        }
    }

    /** 单次关闭语义：返回 {@code true} 表示本次调用赢得了关闭处理权。 */
    boolean terminate() {
        return terminated.compareAndSet(false, true);
    }

    /** 立即放弃连接（不关心对端是否收到关闭帧）。 */
    void abort() {
        WebSocket socket = webSocket;
        if (socket != null) {
            socket.abort();
        }
    }

    /** 主动优雅关闭：等对端确认（限时），超时则 abort。 */
    void closeGracefully() {
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
