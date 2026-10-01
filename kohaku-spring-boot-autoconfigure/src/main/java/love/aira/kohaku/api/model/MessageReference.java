package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 引用回复。
 *
 * <p>{@code messageId} 形如 {@code REFIDX_xxxxxx}：非机器人消息取消息事件 {@code message_scene.ext} 的
 * {@code msg_idx}，机器人自己发的取发消息响应 {@code ext_info.ref_idx}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageReference(String messageId) {

    public static MessageReference of(String messageId) {
        return new MessageReference(messageId);
    }
}
