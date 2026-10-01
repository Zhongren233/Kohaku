package love.aira.kohaku.support;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;

/**
 * 开放平台字段是 snake_case：在宿主传入的 mapper 基础上派生一个专用实例，
 * 避免改动全局 JSON 配置。REST 客户端与事件反序列化共用。
 */
public final class SnakeCaseMappers {

    private SnakeCaseMappers() {
    }

    public static ObjectMapper of(ObjectMapper base) {
        return base.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }
}
