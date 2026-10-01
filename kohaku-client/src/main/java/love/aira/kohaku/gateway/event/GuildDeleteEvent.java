package love.aira.kohaku.gateway.event;

import love.aira.kohaku.gateway.event.model.Guild;
import tools.jackson.databind.JsonNode;

/**
 * 频道解散，事件名见 TYPE 常量，intent GUILDS (1<<0)。
 *
 * <p>强类型事件体见 {@link #payload()}；原始报文体仍可通过 data() 取得。
 */
public final class GuildDeleteEvent extends BotDispatchEvent {

    /** 事件名（网关报文中的 t 字段）。 */
    public static final String TYPE = "GUILD_DELETE";

    private final Guild payload;

    public GuildDeleteEvent(long seq, String eventId, JsonNode raw, Guild payload) {
        super(seq, TYPE, eventId, raw);
        this.payload = payload;
    }

    /** 强类型事件体（字段与官方文档「事件体」一致）。 */
    public Guild payload() {
        return payload;
    }
}
