package love.aira.kohaku.gateway.event.model;

import love.aira.kohaku.gateway.event.model.FriendAuthor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：FriendAdd（字段与官方文档一致）
 *
 * @param timestamp 添加时间戳（Unix 秒）
 * @param openid 用户 OpenID
 * @param scene 加好友场景：1000 默认 1001 网络搜索 1003 群场景 2003 分享链接 等
 * @param sceneParam 开发者自定义回调数据
 * @param author 用户信息
 * @param shortCode 分享短链 code
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FriendAdd(
        Long timestamp,
        String openid,
        Integer scene,
        String sceneParam,
        FriendAuthor author,
        String shortCode) {
}
