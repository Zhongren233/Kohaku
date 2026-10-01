package love.aira.kohaku.api;

import love.aira.kohaku.config.QqBotProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Gateway 接入点接口。
 *
 * <ul>
 *   <li>{@code GET /gateway}：通用接入点（分片数为 1 时使用）</li>
 *   <li>{@code GET /gateway/bot}：带分片接入点，额外返回建议分片数与 Session 创建限制</li>
 * </ul>
 */
@Component
public class QqGatewayApi {

    private final QqOpenApiClient api;
    private final QqBotProperties properties;

    public QqGatewayApi(QqOpenApiClient api, QqBotProperties properties) {
        this.api = api;
        this.properties = properties;
    }

    /** 按配置的分片数选择接口并返回 WSS 接入点。 */
    public String url() {
        JsonNode body = api.get(properties.shardTotal() > 1 ? "/gateway/bot" : "/gateway");
        String url = body.path("url").stringValue(null);
        if (url == null || url.isBlank()) {
            throw new QqApiException("gateway url response has no url field: " + body);
        }
        return url;
    }
}
