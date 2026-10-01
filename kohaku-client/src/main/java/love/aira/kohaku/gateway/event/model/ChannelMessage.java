package love.aira.kohaku.gateway.event.model;

import java.util.List;
import love.aira.kohaku.gateway.event.model.ChannelMessageReference;
import love.aira.kohaku.gateway.event.model.Member;
import love.aira.kohaku.gateway.event.model.MessageArk;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：ChannelMessage（字段与官方文档一致）
 *
 * @param id 消息 id
 * @param channelId 子频道 id
 * @param guildId 频道 id
 * @param content 消息内容
 * @param timestamp 消息创建时间，ISO8601
 * @param editedTimestamp 消息编辑时间，ISO8601
 * @param mentionEveryone 是否 @全员
 * @param author 消息创建者
 * @param attachments 附件
 * @param embeds embed 卡片
 * @param mentions 消息中 @ 的人
 * @param member 创建者的成员信息
 * @param ark ark 消息
 * @param seq 消息排序用序号（2022-08-01 起废弃）
 * @param seqInChannel 子频道内排序序号
 * @param messageReference 引用消息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChannelMessage(
        String id,
        String channelId,
        String guildId,
        String content,
        String timestamp,
        String editedTimestamp,
        Boolean mentionEveryone,
        MessageAuthor author,
        List<MessageAttachment> attachments,
        List<MessageEmbed> embeds,
        List<MessageAuthor> mentions,
        Member member,
        MessageArk ark,
        Integer seq,
        String seqInChannel,
        ChannelMessageReference messageReference) {
}
