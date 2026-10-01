package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 流式消息请求体：{@code POST /v2/users/{user_openid}/stream_messages}（仅单聊）。
 *
 * <p>每个分片携带首个分片返回的 {@code stream_msg_id}，{@code index} 从 0 递增，
 * {@code input_state} 1=生成中、10=生成结束。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamMessageRequest(
        String inputMode,
        Integer inputState,
        Integer index,
        String contentType,
        String contentRaw,
        String eventId,
        String msgId,
        String streamMsgId,
        Integer msgSeq,
        Boolean isWakeup) {

    /** 内容按增量追加到待下发内容（默认）。 */
    public static final String MODE_APPEND = "append";
    /** 内容为当前全量正文，须以上游已下发前缀开头。 */
    public static final String MODE_REPLACE = "replace";
    public static final String CONTENT_TEXT = "text";
    public static final String CONTENT_MARKDOWN = "markdown";
    /** 生成中。 */
    public static final int STATE_GENERATING = 1;
    /** 生成结束。 */
    public static final int STATE_FINISHED = 10;

    /** 首个分片：由服务端生成 stream_msg_id 并在响应 id 中返回。 */
    public static StreamMessageRequest first(String contentRaw, String contentType) {
        return new StreamMessageRequest(MODE_APPEND, STATE_GENERATING, 0, contentType, contentRaw, null, null, null,
                null, null);
    }

    /** 后续分片：携带上一分片返回的 id。 */
    public StreamMessageRequest nextSlice(String streamMsgId, int index, String contentRaw) {
        return new StreamMessageRequest(inputMode, STATE_GENERATING, index, contentType, contentRaw, eventId, msgId,
                streamMsgId, msgSeq, isWakeup);
    }

    /** 结束分片。 */
    public StreamMessageRequest finish(String streamMsgId, int index, String contentRaw) {
        return new StreamMessageRequest(inputMode, STATE_FINISHED, index, contentType, contentRaw, eventId, msgId,
                streamMsgId, msgSeq, isWakeup);
    }

    public StreamMessageRequest withMsgId(String msgId) {
        return new StreamMessageRequest(inputMode, inputState, index, contentType, contentRaw, eventId, msgId,
                streamMsgId, msgSeq, isWakeup);
    }

    public StreamMessageRequest withEventId(String eventId) {
        return new StreamMessageRequest(inputMode, inputState, index, contentType, contentRaw, eventId, msgId,
                streamMsgId, msgSeq, isWakeup);
    }

    public StreamMessageRequest withMsgSeq(int msgSeq) {
        return new StreamMessageRequest(inputMode, inputState, index, contentType, contentRaw, eventId, msgId,
                streamMsgId, msgSeq, isWakeup);
    }

    public StreamMessageRequest wakeup() {
        return new StreamMessageRequest(inputMode, inputState, index, contentType, contentRaw, eventId, msgId,
                streamMsgId, msgSeq, true);
    }
}
