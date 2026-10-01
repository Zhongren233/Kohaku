package love.aira.kohaku.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * 开放平台 HTTP 接口客户端：AccessToken 换取与缓存、Gateway 接入点获取。
 *
 * <p>AccessToken 有效期（通常 7200s）内复用，提前 {@value #EXPIRY_SKEW_SECONDS} 秒过期；
 * Gateway 地址在同一进程内缓存，重连时复用。
 */
@Component
public class QqApiClient {

    private static final Logger log = LoggerFactory.getLogger(QqApiClient.class);
    private static final long EXPIRY_SKEW_SECONDS = 60;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String TOKEN_PREFIX = "QQBot ";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final QqBotProperties properties;

    private final Object lock = new Object();
    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;
    private String gatewayUrl;

    public QqApiClient(HttpClient qqHttpClient, ObjectMapper mapper, QqBotProperties properties) {
        this.httpClient = qqHttpClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    /** 返回带 {@code QQBot } 前缀的鉴权头值，必要时自动刷新。 */
    public String authorization() {
        synchronized (lock) {
            if (accessToken == null || !Instant.now().isBefore(accessTokenExpiresAt.minusSeconds(EXPIRY_SKEW_SECONDS))) {
                refreshAccessToken();
            }
            return TOKEN_PREFIX + accessToken;
        }
    }

    /** 获取 Gateway 的 WSS 接入点，结果缓存至进程结束。 */
    public String gatewayUrl() {
        synchronized (lock) {
            if (gatewayUrl != null) {
                return gatewayUrl;
            }
            // 文档：通用接入点 GET /gateway；带分片接入点 GET /gateway/bot（额外返回建议分片数与 Session 限制）
            String path = properties.shardTotal() > 1 ? "/gateway/bot" : "/gateway";
            HttpResponse<String> response = get(properties.apiBaseUrl() + path, authorization());
            if (isUnauthorized(response.statusCode())) {
                log.info("gateway url request rejected with {}, refreshing access token", response.statusCode());
                invalidateAccessToken();
                response = get(properties.apiBaseUrl() + path, authorization());
            }
            JsonNode body = requireSuccess("gateway url", response);
            String url = body.path("url").stringValue(null);
            if (url == null || url.isBlank()) {
                throw new QqApiException("gateway url response has no url field: " + response.body());
            }
            gatewayUrl = url;
            log.info("resolved gateway at {}", url);
            return url;
        }
    }

    public void invalidateAccessToken() {
        synchronized (lock) {
            accessToken = null;
            accessTokenExpiresAt = Instant.EPOCH;
        }
    }

    private void refreshAccessToken() {
        ObjectNode body = mapper.createObjectNode();
        body.put("appId", properties.appId());
        body.put("clientSecret", properties.appSecret());
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenUrl()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = send(request);
        JsonNode payload = requireSuccess("access token", response);
        JsonNode token = payload.get("access_token");
        if (token == null || !token.isString() || token.stringValue().isBlank()) {
            throw new QqApiException("access token response has no access_token field: " + response.body());
        }
        long expiresIn = payload.path("expires_in").asLong(7200);
        accessToken = token.stringValue();
        accessTokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        log.debug("access token refreshed, expires in {}s", expiresIn);
    }

    private HttpResponse<String> get(String url, String authorization) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorization)
                .GET()
                .build());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new QqApiException("request to " + request.uri() + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QqApiException("request to " + request.uri() + " was interrupted", e);
        }
    }

    private JsonNode requireSuccess(String operation, HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            throw new QqApiException(operation + " failed with HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode payload = mapper.readTree(response.body());
        int code = payload.path("code").asInt(0);
        if (code != 0) {
            throw new QqApiException(operation + " failed with code " + code + ": " + response.body());
        }
        return payload;
    }

    private static boolean isUnauthorized(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }
}
