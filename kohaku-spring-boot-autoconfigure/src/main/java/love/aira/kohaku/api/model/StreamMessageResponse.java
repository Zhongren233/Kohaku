package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 流式消息响应。
 *
 * @param id           消息 ID；首个分片返回的即后续分片要携带的 stream_msg_id
 * @param timestamp    发送时间
 * @param extInfo      扩展信息
 * @param remainMsgLen 剩余可下发字符数
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StreamMessageResponse(String id, String timestamp, MessageExtInfo extInfo, Integer remainMsgLen) {
}
