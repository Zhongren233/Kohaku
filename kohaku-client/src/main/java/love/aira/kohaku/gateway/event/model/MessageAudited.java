package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：MessageAudited（字段与官方文档一致）
 *
 * @param auditId 审核 id
 * @param messageId 消息 id（仅审核通过事件有值）
 * @param guildId 频道 id
 * @param channelId 子频道 id
 * @param auditTime 审核时间，ISO8601
 * @param createTime 消息创建时间，ISO8601
 * @param seqInChannel 子频道内排序序号
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageAudited(
        String auditId,
        String messageId,
        String guildId,
        String channelId,
        String auditTime,
        String createTime,
        String seqInChannel) {
}
