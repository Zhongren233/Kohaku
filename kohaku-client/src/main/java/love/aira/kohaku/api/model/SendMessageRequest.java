package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 发送消息请求体，单聊 {@code POST /v2/users/{user_openid}/messages} 与
 * 群聊 {@code POST /v2/groups/{group_openid}/messages} 共用。
 *
 * <p>被动回复（填 {@code msg_id} 或 {@code event_id} 之一）与主动消息（都不填）由字段决定；
 * 单聊被动回复有效期 60 分钟、最多回复 4 次，群聊 5 分钟、最多 5 次；
 * 相同 {@code msg_id + msg_seq} 重复发送会失败，可用 {@link #withMsgSeq(int)} 递增。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageRequest(
        Integer msgType,
        String content,
        MarkdownMessage markdown,
        Media media,
        Keyboard keyboard,
        MessageReference messageReference,
        String msgId,
        String eventId,
        Integer msgSeq,
        Boolean isWakeup,
        InputNotify inputNotify) {

    /** 纯文本。 */
    public static final int TYPE_TEXT = 0;
    /** Markdown。 */
    public static final int TYPE_MARKDOWN = 2;
    /** 输入中状态（仅单聊）。 */
    public static final int TYPE_INPUT_NOTIFY = 6;
    /** 富媒体。 */
    public static final int TYPE_MEDIA = 7;

    public static SendMessageRequest text(String content) {
        return new SendMessageRequest(TYPE_TEXT, content, null, null, null, null, null, null, null, null, null);
    }

    public static SendMessageRequest markdown(String content) {
        return new SendMessageRequest(TYPE_MARKDOWN, null, MarkdownMessage.of(content), null, null, null, null, null,
                null, null, null);
    }

    public static SendMessageRequest markdown(MarkdownMessage markdown) {
        return new SendMessageRequest(TYPE_MARKDOWN, null, markdown, null, null, null, null, null, null, null, null);
    }

    public static SendMessageRequest media(String fileInfo) {
        return new SendMessageRequest(TYPE_MEDIA, null, null, Media.of(fileInfo), null, null, null, null, null, null,
                null);
    }

    /** 输入中状态，{@code seconds} 最长 60。 */
    public static SendMessageRequest typing(int seconds) {
        return new SendMessageRequest(TYPE_INPUT_NOTIFY, null, null, null, null, null, null, null, null, null,
                InputNotify.typing(seconds));
    }

    /** 被动回复某条消息（默认 msg_seq 由平台按 1 处理）。 */
    public SendMessageRequest replyingTo(String msgId) {
        return new SendMessageRequest(msgType, content, markdown, media, keyboard, messageReference, msgId, eventId,
                msgSeq, isWakeup, inputNotify);
    }

    /** 被动回复某条消息的第 {@code msgSeq} 次回复。 */
    public SendMessageRequest replyingTo(String msgId, int msgSeq) {
        return replyingTo(msgId).withMsgSeq(msgSeq);
    }

    /** 被动回复某个事件（INTERACTION_CREATE、C2C_MSG_RECEIVE、FRIEND_ADD、GROUP_ADD_ROBOT 等）。 */
    public SendMessageRequest replyingToEvent(String eventId) {
        return new SendMessageRequest(msgType, content, markdown, media, keyboard, messageReference, msgId, eventId,
                msgSeq, isWakeup, inputNotify);
    }

    public SendMessageRequest withMsgSeq(int msgSeq) {
        return new SendMessageRequest(msgType, content, markdown, media, keyboard, messageReference, msgId, eventId,
                msgSeq, isWakeup, inputNotify);
    }

    public SendMessageRequest withKeyboard(Keyboard keyboard) {
        return new SendMessageRequest(msgType, content, markdown, media, keyboard, messageReference, msgId, eventId,
                msgSeq, isWakeup, inputNotify);
    }

    /** 引用回复（message_id 形如 REFIDX_xxx）。 */
    public SendMessageRequest quoting(String messageId) {
        return new SendMessageRequest(msgType, content, markdown, media, keyboard,
                MessageReference.of(messageId), msgId, eventId, msgSeq, isWakeup, inputNotify);
    }

    /** 互动召回消息（与 msg_id/event_id 互斥）。 */
    public SendMessageRequest wakeup() {
        return new SendMessageRequest(msgType, content, markdown, media, keyboard, messageReference, msgId, eventId,
                msgSeq, true, inputNotify);
    }
}
