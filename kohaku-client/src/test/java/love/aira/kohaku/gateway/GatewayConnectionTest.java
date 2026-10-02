package love.aira.kohaku.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** 连接层：分帧拼接、请求更多帧、关闭只处理一次、优雅关闭。 */
class GatewayConnectionTest {

    private final FakeSocket socket = new FakeSocket();
    private final RecordingEvents events = new RecordingEvents();
    private final GatewayConnection connection = new GatewayConnection(false, events);

    @Test
    void assemblesFragmentedFramesIntoOneMessage() {
        connection.onOpen(socket);

        connection.onText(socket, "{\"op\":", false);
        connection.onText(socket, "10,\"d\":{}}", true);

        assertThat(events.messages).containsExactly("{\"op\":10,\"d\":{}}");
        assertThat(socket.requests).isEqualTo(3);   // onOpen + 每个分片各一次
    }

    @Test
    void deliversEachCompleteFrameSeparately() {
        connection.onOpen(socket);

        connection.onText(socket, "{\"op\":1}", true);
        connection.onText(socket, "{\"op\":11}", true);

        assertThat(events.messages).containsExactly("{\"op\":1}", "{\"op\":11}");
    }

    @Test
    void handlesCloseOnlyOnce() {
        connection.onOpen(socket);

        connection.onClose(socket, 1000, "bye");
        connection.onClose(socket, GatewayCloseCode.ABNORMAL, "again");
        connection.onError(socket, new IllegalStateException("boom"));

        assertThat(events.closes).containsExactly("1000:bye");
        assertThat(socket.aborts).isEqualTo(1);     // 只来自 onError
    }

    @Test
    void errorWithoutCloseStillReportsAbnormalAndAborts() {
        connection.onOpen(socket);

        connection.onError(socket, new IllegalStateException("boom"));

        assertThat(events.closes).hasSize(1);
        assertThat(events.closes.getFirst()).startsWith(GatewayCloseCode.ABNORMAL + ":");
        assertThat(socket.aborts).isEqualTo(1);
    }

    @Test
    void closeGracefullySendsNormalClosure() {
        connection.onOpen(socket);

        connection.closeGracefully();

        assertThat(socket.closeCode).isEqualTo(WebSocket.NORMAL_CLOSURE + ":shutdown");
    }

    @Test
    void exposesResumeAndHelloFlags() {
        assertThat(connection.resume()).isFalse();
        assertThat(connection.helloReceived()).isFalse();

        connection.markHelloReceived();

        assertThat(connection.helloReceived()).isTrue();
        assertThat(new GatewayConnection(true, events).resume()).isTrue();
    }

    /** 最小 WebSocket 假实现：只记录 request/abort/sendClose。 */
    private static final class FakeSocket implements WebSocket {

        private int requests;
        private int aborts;
        private String closeCode;

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            closeCode = statusCode + ":" + reason;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
            requests++;
        }

        @Override
        public String getSubprotocol() {
            return null;
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {
            aborts++;
        }
    }

    private static final class RecordingEvents implements GatewayConnection.Events {

        private final List<String> messages = new ArrayList<>();
        private final List<String> closes = new ArrayList<>();

        @Override
        public void onMessage(GatewayConnection connection, String text) {
            messages.add(text);
        }

        @Override
        public void onClosed(GatewayConnection connection, int code, String reason) {
            closes.add(code + ":" + reason);
        }
    }
}
