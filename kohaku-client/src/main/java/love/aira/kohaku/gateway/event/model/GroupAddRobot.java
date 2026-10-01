package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：GroupAddRobot（字段与官方文档一致）
 *
 * @param groupOpenid 群 OpenID
 * @param opMemberOpenid 操作进群的成员 OpenID
 * @param timestamp 加入时间戳（Unix 秒）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupAddRobot(
        String groupOpenid,
        String opMemberOpenid,
        Long timestamp) {
}
