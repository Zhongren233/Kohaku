package love.aira.kohaku.gateway.event;

import love.aira.kohaku.gateway.event.model.GroupMessage;
import tools.jackson.databind.JsonNode;

/**
 * 群内 @机器人 消息，事件名见 TYPE 常量，intent GROUP_AND_C2C_EVENT (1<<25)。
 *
 * <p>强类型事件体见 {@link #payload()}；原始报文体仍可通过 data() 取得。
 */
public final class GroupAtMessageCreateEvent extends BotDispatchEvent {

    /** 事件名（网关报文中的 t 字段）。 */
    public static final String TYPE = "GROUP_AT_MESSAGE_CREATE";

    private final GroupMessage payload;

    public GroupAtMessageCreateEvent(long seq, String eventId, JsonNode raw, GroupMessage payload) {
        super(seq, TYPE, eventId, raw);
        this.payload = payload;
    }

    /** 强类型事件体（字段与官方文档「事件体」一致）。 */
    public GroupMessage payload() {
        return payload;
    }
}
