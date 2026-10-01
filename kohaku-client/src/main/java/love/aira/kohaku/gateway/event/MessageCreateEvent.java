package love.aira.kohaku.gateway.event;

import love.aira.kohaku.gateway.event.model.ChannelMessage;
import tools.jackson.databind.JsonNode;

/**
 * 频道全量消息（私域），事件名见 TYPE 常量，intent GUILD_MESSAGES (1<<9)。
 *
 * <p>强类型事件体见 {@link #payload()}；原始报文体仍可通过 data() 取得。
 */
public final class MessageCreateEvent extends BotDispatchEvent {

    /** 事件名（网关报文中的 t 字段）。 */
    public static final String TYPE = "MESSAGE_CREATE";

    private final ChannelMessage payload;

    public MessageCreateEvent(long seq, String eventId, JsonNode raw, ChannelMessage payload) {
        super(seq, TYPE, eventId, raw);
        this.payload = payload;
    }

    /** 强类型事件体（字段与官方文档「事件体」一致）。 */
    public ChannelMessage payload() {
        return payload;
    }
}
