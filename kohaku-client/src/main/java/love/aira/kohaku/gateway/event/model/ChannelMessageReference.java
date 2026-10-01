package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：ChannelMessageReference（字段与官方文档一致）
 *
 * @param messageId 需要引用回复的消息 id
 * @param ignoreGetMessageError 是否忽略获取引用消息详情错误
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChannelMessageReference(
        String messageId,
        Boolean ignoreGetMessageError) {
}
