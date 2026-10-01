package love.aira.kohaku.gateway.event;

import tools.jackson.databind.JsonNode;

/**
 * 除 READY / RESUMED 外的事件推送（OpCode 0）。
 *
 * @param type 事件类型，如 AT_MESSAGE_CREATE
 * @param data 原始事件数据体
 */
public class BotDispatchEvent extends BotEvent {

    private final String type;
    private final JsonNode data;

    public BotDispatchEvent(Object source, long seq, String type, JsonNode data) {
        super(source, seq);
        this.type = type;
        this.data = data;
    }

    public String type() {
        return type;
    }

    public JsonNode data() {
        return data;
    }
}
