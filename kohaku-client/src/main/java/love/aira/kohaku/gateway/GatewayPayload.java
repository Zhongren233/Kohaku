package love.aira.kohaku.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * Gateway 上下行统一报文结构。
 *
 * @param op OpCode
 * @param d  数据体，随 op 变化
 * @param s  事件序号，客户端需记录最近一次收到的值用于 Resume
 * @param t  事件类型，仅 Dispatch 存在
 * @param id 事件对外 id（最外层 id，形如 {@code INTERACTION_CREATE:963df69c-…}），
 *           被动回复互动/消息事件时作为 {@code event_id} 上报；并非所有事件都有
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayPayload(int op, JsonNode d, Long s, String t, String id) {
}
