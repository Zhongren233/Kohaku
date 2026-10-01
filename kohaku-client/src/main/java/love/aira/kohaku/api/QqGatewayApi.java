package love.aira.kohaku.api;

import love.aira.kohaku.config.KohakuConfig;
import tools.jackson.databind.JsonNode;

/**
 * Gateway 接入点接口。
 *
 * <ul>
 *   <li>{@code GET /gateway}：通用接入点（分片数为 1 时使用）</li>
 *   <li>{@code GET /gateway/bot}：带分片接入点，额外返回建议分片数与 Session 创建限制</li>
 * </ul>
 */
public class QqGatewayApi {

    private final QqOpenApiClient api;
    private final KohakuConfig config;

    public QqGatewayApi(QqOpenApiClient api, KohakuConfig config) {
        this.api = api;
        this.config = config;
    }

    /** 按配置的分片数选择接口并返回 WSS 接入点。 */
    public String url() {
        JsonNode body = api.get(config.shardTotal() > 1 ? "/gateway/bot" : "/gateway");
        String url = body.path("url").stringValue(null);
        if (url == null || url.isBlank()) {
            throw new QqApiException("gateway url response has no url field: " + body);
        }
        return url;
    }
}
