package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：MessageArkObjKv（字段与官方文档一致）
 *
 * @param key key
 * @param value value
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageArkObjKv(
        String key,
        String value) {
}
