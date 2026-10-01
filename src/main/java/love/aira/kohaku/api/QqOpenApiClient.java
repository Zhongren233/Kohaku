package love.aira.kohaku.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;
import love.aira.kohaku.config.QqBotProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;

/**
 * 开放平台 REST 调用器：统一拼接 base url、注入 {@code Authorization}、解析 JSON、
 * 把 HTTP 层与业务层错误转成 {@link QqApiException}，并在 401/403 时刷新 AccessToken 重试一次。
 *
 * <p>平台两种错误形态都会处理：非 2xx（如 401/404）与 2xx 但响应体 {@code code != 0}。
 */
@Component
public class QqOpenApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final QqBotProperties properties;
    private final AccessTokenProvider tokens;

    public QqOpenApiClient(HttpClient qqHttpClient, ObjectMapper mapper, QqBotProperties properties,
                           AccessTokenProvider tokens) {
        this.httpClient = qqHttpClient;
        // 平台字段是 snake_case：在共享 mapper 基础上派生专用实例，避免改动全局 JSON 配置
        this.mapper = mapper.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        this.properties = properties;
        this.tokens = tokens;
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return convert(post(path, body), type);
    }

    public JsonNode post(String path, Object body) {
        return execute(authorization -> request(path, authorization)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : mapper.writeValueAsString(body)))
                .build());
    }

    public <T> T get(String path, Class<T> type) {
        return convert(get(path), type);
    }

    public JsonNode get(String path) {
        return execute(authorization -> request(path, authorization).GET().build());
    }

    public <T> T delete(String path, Class<T> type) {
        return convert(delete(path), type);
    }

    public JsonNode delete(String path) {
        return execute(authorization -> request(path, authorization).DELETE().build());
    }

    private HttpRequest.Builder request(String path, String authorization) {
        return HttpRequest.newBuilder(URI.create(properties.apiBaseUrl() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorization);
    }

    private JsonNode execute(Function<String, HttpRequest> requestFactory) {
        HttpResponse<String> response = HttpCalls.send(httpClient, requestFactory.apply(tokens.authorization()));
        if (isUnauthorized(response.statusCode())) {
            tokens.invalidate();
            response = HttpCalls.send(httpClient, requestFactory.apply(tokens.authorization()));
        }
        return ApiResponses.requireSuccess(response);
    }

    private <T> T convert(JsonNode payload, Class<T> type) {
        try {
            return mapper.treeToValue(payload, type);
        } catch (JacksonException e) {
            throw new QqApiException("cannot map response to " + type.getSimpleName() + ": " + payload, e);
        }
    }

    private static boolean isUnauthorized(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }
}
