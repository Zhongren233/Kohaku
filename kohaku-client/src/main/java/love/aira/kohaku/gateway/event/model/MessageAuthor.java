package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件中的用户/成员信息（文档 User 对象）。
 *
 * @param id               用户唯一标识（OpenID 格式）
 * @param username         用户昵称
 * @param bot              是否为机器人
 * @param avatar           用户头像地址（频道场景的 User 有该字段，单聊/群聊场景无）
 * @param unionOpenid      跨应用统一用户 OpenID（可能为空）
 * @param unionUserAccount 跨应用统一用户账号（可能为空）
 * @param userOpenid       用户 OpenID（单聊场景使用）
 * @param memberOpenid     群成员 OpenID（群聊场景使用）
 * @param memberRole       群内角色：member=普通成员、admin=管理员、owner=群主
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageAuthor(
        String id,
        String username,
        Boolean bot,
        String avatar,
        String unionOpenid,
        String unionUserAccount,
        String userOpenid,
        String memberOpenid,
        String memberRole) {
}
