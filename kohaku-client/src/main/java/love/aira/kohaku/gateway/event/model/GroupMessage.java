package love.aira.kohaku.gateway.event.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：GroupMessage（字段与官方文档一致）
 *
 * @param id 消息 id，可用于被动回复与撤回
 * @param author 发送者（member_openid 有值）
 * @param content 消息文本内容。群 @ 消息（GROUP_AT_MESSAGE_CREATE）平台已去掉 @机器人 前缀；
 *                群全量消息（GROUP_MESSAGE_CREATE）**原样保留** {@code <@机器人id>}，
 *                命令匹配前请用 {@link love.aira.kohaku.support.Mentions#stripLeading} 剥离
 * @param groupOpenid 群 OpenID
 * @param timestamp 发送时间，RFC3339
 * @param messageType 内容类型：0=文本 3=卡片 101=并行消息 102=聊天记录 103=引用消息
 * @param messageScene 消息场景上下文
 * @param attachments 附件
 * @param mentions 消息中被 @ 的用户。群 @ 消息通常不下发该字段；群全量消息会下发且**包含机器人自身**
 *                 （其 {@code bot=true}），可用于判断行首提及是否属于机器人
 * @param arkData 结构化卡片
 * @param msgElements 消息元素
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupMessage(
        String id,
        MessageAuthor author,
        String content,
        String groupOpenid,
        String timestamp,
        Integer messageType,
        MessageScene messageScene,
        List<MessageAttachment> attachments,
        List<MessageAuthor> mentions,
        ArkData arkData,
        List<MsgElement> msgElements) {
}
