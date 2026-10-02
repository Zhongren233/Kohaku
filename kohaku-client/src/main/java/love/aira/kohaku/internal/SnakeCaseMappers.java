package love.aira.kohaku.internal;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;

/**
 * 开放平台字段是 snake_case：在宿主传入的 mapper 基础上派生一个专用实例，
 * 避免改动全局 JSON 配置。REST 客户端与事件反序列化共用。
 *
 * <p><b>内部实现细节，不属于公开 API</b>：跨包共用（api 与 gateway）才保持 public，使用者不应依赖。
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
