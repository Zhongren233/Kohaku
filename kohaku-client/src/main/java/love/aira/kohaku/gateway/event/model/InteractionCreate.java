package love.aira.kohaku.gateway.event.model;

import love.aira.kohaku.gateway.event.model.InteractionData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：InteractionCreate（字段与官方文档一致）
 *
 * @param id 事件 id，用于被动消息与互动回调
 * @param type 互动类型：11 按钮 12 快捷菜单 13 消息反馈 14 清空会话 15 故事集 16 切换模型 18/19 授权 20 授权状态
 * @param scene 场景：c2c=单聊 group=群聊 guild=频道
 * @param chatType 聊天场景：0 频道 1 群聊 2 单聊
 * @param timestamp 触发时间，RFC3339
 * @param guildId 频道 OpenID（仅频道）
 * @param channelId 子频道 OpenID（仅频道）
 * @param userOpenid 用户 OpenID（仅单聊）
 * @param groupOpenid 群 OpenID（仅群聊）
 * @param groupMemberOpenid 群成员 OpenID（仅群聊）
 * @param data 互动数据
 * @param version 版本号，默认 1
 * @param applicationId 机器人 AppID
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InteractionCreate(
        String id,
        Integer type,
        String scene,
        Integer chatType,
        String timestamp,
        String guildId,
        String channelId,
        String userOpenid,
        String groupOpenid,
        String groupMemberOpenid,
        InteractionData data,
        Integer version,
        String applicationId) {
}
