package love.aira.kohaku.api;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** 同步 HTTP 调用的公共包装：把 IOException/InterruptedException 统一转为 {@link QqApiException}。 */
final class HttpCalls {

    private HttpCalls() {
    }

    static HttpResponse<String> send(HttpClient httpClient, HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new QqApiException("request to " + request.uri() + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QqApiException("request to " + request.uri() + " was interrupted", e);
        }
    }
}
