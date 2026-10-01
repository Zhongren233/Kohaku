package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：MessageEmbedThumbnail（字段与官方文档一致）
 *
 * @param url 图片地址
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageEmbedThumbnail(
        String url) {
}
