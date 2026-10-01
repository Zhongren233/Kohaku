package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：MessageEmbedField（字段与官方文档一致）
 *
 * @param name 字段名
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageEmbedField(
        String name) {
}
