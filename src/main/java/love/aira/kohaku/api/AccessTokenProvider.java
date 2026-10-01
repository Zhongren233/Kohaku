package love.aira.kohaku.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import love.aira.kohaku.config.QqBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * AccessToken 的换取与缓存：有效期（通常 7200s）内复用，提前 {@value #EXPIRY_SKEW_SECONDS} 秒过期；
 * 被服务端拒绝时可 {@link #invalidate()} 后立即重取。
 */
@Component
public class AccessTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenProvider.class);
    private static final long EXPIRY_SKEW_SECONDS = 60;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String TOKEN_PREFIX = "QQBot ";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final QqBotProperties properties;

    private final Object lock = new Object();
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public AccessTokenProvider(HttpClient qqHttpClient, ObjectMapper mapper, QqBotProperties properties) {
        this.httpClient = qqHttpClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    /** 返回可直接用于 {@code Authorization} 的值，形如 {@code QQBot xxxxx}，必要时自动刷新。 */
    public String authorization() {
        synchronized (lock) {
            if (accessToken == null || !Instant.now().isBefore(expiresAt.minusSeconds(EXPIRY_SKEW_SECONDS))) {
                refresh();
            }
            return TOKEN_PREFIX + accessToken;
        }
    }

    /** 丢弃当前 token，下次 {@link #authorization()} 重新换取（用于 401/403 或鉴权失败重试）。 */
    public void invalidate() {
        synchronized (lock) {
            accessToken = null;
            expiresAt = Instant.EPOCH;
        }
    }

    private void refresh() {
        ObjectNode body = mapper.createObjectNode();
        body.put("appId", properties.appId());
        body.put("clientSecret", properties.appSecret());
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenUrl()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        JsonNode payload = QqOpenApiClient.requireSuccess(HttpCalls.send(httpClient, request));
        JsonNode token = payload.get("access_token");
        if (token == null || !token.isString() || token.stringValue().isBlank()) {
            throw new QqApiException("access token response has no access_token field: " + payload);
        }
        long expiresIn = payload.path("expires_in").asLong(7200);
        accessToken = token.stringValue();
        expiresAt = Instant.now().plusSeconds(expiresIn);
        log.debug("access token refreshed, expires in {}s", expiresIn);
    }
}
