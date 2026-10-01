package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：C2cMsgReceive（字段与官方文档一致）
 *
 * @param openid 用户 OpenID
 * @param timestamp 操作时间戳（Unix 秒）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record C2cMsgReceive(
        String openid,
        Long timestamp) {
}
