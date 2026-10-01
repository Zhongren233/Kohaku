package love.aira.kohaku.gateway.event;

import love.aira.kohaku.gateway.event.model.Channel;
import tools.jackson.databind.JsonNode;

/**
 * 子频道更新，事件名见 TYPE 常量，intent GUILDS (1<<0)。
 *
 * <p>强类型事件体见 {@link #payload()}；原始报文体仍可通过 data() 取得。
 */
public final class ChannelUpdateEvent extends BotDispatchEvent {

    /** 事件名（网关报文中的 t 字段）。 */
    public static final String TYPE = "CHANNEL_UPDATE";

    private final Channel payload;

    public ChannelUpdateEvent(long seq, JsonNode raw, Channel payload) {
        super(seq, TYPE, raw);
        this.payload = payload;
    }

    /** 强类型事件体（字段与官方文档「事件体」一致）。 */
    public Channel payload() {
        return payload;
    }
}
