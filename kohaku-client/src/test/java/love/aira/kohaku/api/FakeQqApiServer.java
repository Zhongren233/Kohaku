package love.aira.kohaku.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于 JDK HttpServer 的开放平台假服务端：按 {@code METHOD /path} 预置响应，并记录收到的请求
 * （方法、路径、Authorization、请求体），用于断言接口的路径/报文/鉴权与错误处理。
 */
public final class FakeQqApiServer implements AutoCloseable {

    public record Call(String method, String path, String query, String authorization, String contentType, String body) {
    }

    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Map<String, Deque<String[]>> stubs = new ConcurrentHashMap<>();

    public FakeQqApiServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 预置响应；同一 key 可多次预置，按顺序消费，用尽后返回 500 便于发现多余请求。 */
    @SafeVarargs
    public final void stub(String methodAndPath, Map.Entry<Integer, String>... responses) {
        Deque<String[]> queue = stubs.computeIfAbsent(methodAndPath, key -> new ArrayDeque<>());
        for (Map.Entry<Integer, String> response : responses) {
            queue.add(new String[] {String.valueOf(response.getKey()), response.getValue()});
        }
    }

    public void stub(String methodAndPath, int status, String body) {
        stubs.computeIfAbsent(methodAndPath, key -> new ArrayDeque<>())
                .add(new String[] {String.valueOf(status), body});
    }

    public List<Call> calls(String methodAndPath) {
        return calls.stream().filter(call -> methodAndPath.equals(call.method() + " " + call.path())).toList();
    }

    public List<Call> calls() {
        return new ArrayList<>(calls);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(new Call(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"), body));

        Deque<String[]> queue = stubs.get(exchange.getRequestMethod() + " " + path);
        if (queue == null || queue.isEmpty()) {
            respond(exchange, 500, "{\"code\":500,\"message\":\"no stub for "
                    + exchange.getRequestMethod() + " " + path + "\"}");
            return;
        }
        String[] response = queue.poll();
        respond(exchange, Integer.parseInt(response[0]), response[1]);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
