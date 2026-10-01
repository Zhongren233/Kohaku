package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：GroupMsgReject（字段与官方文档一致）
 *
 * @param groupOpenid 群 OpenID
 * @param opMemberOpenid 操作成员 OpenID
 * @param timestamp 操作时间戳（Unix 秒）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupMsgReject(
        String groupOpenid,
        String opMemberOpenid,
        Long timestamp) {
}
