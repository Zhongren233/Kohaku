package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 消息元素（文档 MsgElement，{@code message_type=103} 引用消息时包含被引用内容；可递归嵌套）。
 *
 * @param msgIdx      引用消息索引
 * @param author      该元素对应的消息发送者
 * @param messageType 内容类型：0=普通文本、3=结构化卡片、101=并行消息、102=聊天记录、103=引用消息
 * @param content     消息正文
 * @param attachments 该元素携带的附件
 * @param arkData     结构化卡片数据（message_type=3 时有值）
 * @param msgElements 嵌套消息元素（递归）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MsgElement(
        String msgIdx,
        MessageAuthor author,
        Integer messageType,
        String content,
        List<MessageAttachment> attachments,
        ArkData arkData,
        List<MsgElement> msgElements) {
}
