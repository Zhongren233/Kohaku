package love.aira.kohaku.gateway.event;

import love.aira.kohaku.gateway.event.model.ChannelMessage;
import tools.jackson.databind.JsonNode;

/**
 * 频道内 @机器人 消息，事件名见 TYPE 常量，intent PUBLIC_GUILD_MESSAGES (1<<30)。
 *
 * <p>强类型事件体见 {@link #payload()}；原始报文体仍可通过 data() 取得。
 */
public final class AtMessageCreateEvent extends BotDispatchEvent {

    /** 事件名（网关报文中的 t 字段）。 */
    public static final String TYPE = "AT_MESSAGE_CREATE";

    private final ChannelMessage payload;

    public AtMessageCreateEvent(long seq, String eventId, JsonNode raw, ChannelMessage payload) {
        super(seq, TYPE, eventId, raw);
        this.payload = payload;
    }

    /** 强类型事件体（字段与官方文档「事件体」一致）。 */
    public ChannelMessage payload() {
        return payload;
    }
}
