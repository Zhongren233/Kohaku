package love.aira.kohaku.gateway.event.model;

import love.aira.kohaku.gateway.event.model.FriendAuthor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：FriendDel（字段与官方文档一致）
 *
 * @param timestamp 删除时间戳（Unix 秒）
 * @param openid 用户 OpenID
 * @param author 用户信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FriendDel(
        Long timestamp,
        String openid,
        FriendAuthor author) {
}
