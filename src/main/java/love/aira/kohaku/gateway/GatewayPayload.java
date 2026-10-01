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
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayPayload(int op, JsonNode d, Long s, String t) {
}
