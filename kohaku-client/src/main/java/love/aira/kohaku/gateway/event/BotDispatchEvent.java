package love.aira.kohaku.gateway.event;

import tools.jackson.databind.JsonNode;

/**
 * 除 READY / RESUMED 外的事件推送（OpCode 0）。
 *
 * @param seq     事件序号 s
 * @param type    事件类型，如 AT_MESSAGE_CREATE
 * @param eventId 事件最外层 id（形如 {@code C2C_MESSAGE_CREATE:eqkb…}），被动回复时作为 {@code event_id}
 * @param data    原始事件数据体
 */
public class BotDispatchEvent extends BotEvent {

    private final String type;
    private final String eventId;
    private final JsonNode data;

    public BotDispatchEvent(long seq, String type, String eventId, JsonNode data) {
        super(seq);
        this.type = type;
        this.eventId = eventId;
        this.data = data;
    }

    public String type() {
        return type;
    }

    /** 事件最外层 id，用作被动回复的 {@code event_id}（文档：event_id 从事件最外层 id 获取）。 */
    public String eventId() {
        return eventId;
    }

    /** 原始事件数据体（{@code d}）。 */
    public JsonNode data() {
        return data;
    }
}
