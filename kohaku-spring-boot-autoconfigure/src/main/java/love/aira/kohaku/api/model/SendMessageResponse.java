package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 发送消息响应（单聊/群聊），频道场景返回的 Message 对象也按此接收（多余字段忽略）。
 *
 * @param id        消息 ID，可用于撤回
 * @param timestamp 发送时间，RFC3339
 * @param extInfo   扩展信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SendMessageResponse(String id, String timestamp, MessageExtInfo extInfo) {

    /** 该消息被引用时对外暴露的索引。 */
    public String refIdx() {
        return extInfo == null ? null : extInfo.refIdx();
    }
}
