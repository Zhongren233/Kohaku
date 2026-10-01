package love.aira.kohaku.gateway.event.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：C2cMessage（字段与官方文档一致）
 *
 * @param id 消息 id，可用于被动回复与撤回
 * @param author 发送者（user_openid 有值）
 * @param content 消息文本内容
 * @param timestamp 发送时间，RFC3339
 * @param messageType 内容类型：0=文本 3=卡片 101=并行消息 102=聊天记录 103=引用消息
 * @param messageScene 消息场景上下文
 * @param attachments 附件
 * @param arkData 结构化卡片（message_type=3）
 * @param msgElements 消息元素（message_type=103 引用消息）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record C2cMessage(
        String id,
        MessageAuthor author,
        String content,
        String timestamp,
        Integer messageType,
        MessageScene messageScene,
        List<MessageAttachment> attachments,
        ArkData arkData,
        List<MsgElement> msgElements) {
}
