package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：FriendAuthor（字段与官方文档一致）
 *
 * @param unionOpenid 跨应用统一 OpenID
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FriendAuthor(
        String unionOpenid) {
}
