package love.aira.kohaku.gateway.event;

import love.aira.kohaku.gateway.event.model.MessageAudited;
import tools.jackson.databind.JsonNode;

/**
 * 消息审核通过，事件名见 TYPE 常量，intent MESSAGE_AUDIT (1<<27)。
 *
 * <p>强类型事件体见 {@link #payload()}；原始报文体仍可通过 data() 取得。
 */
public final class MessageAuditPassEvent extends BotDispatchEvent {

    /** 事件名（网关报文中的 t 字段）。 */
    public static final String TYPE = "MESSAGE_AUDIT_PASS";

    private final MessageAudited payload;

    public MessageAuditPassEvent(long seq, String eventId, JsonNode raw, MessageAudited payload) {
        super(seq, TYPE, eventId, raw);
        this.payload = payload;
    }

    /** 强类型事件体（字段与官方文档「事件体」一致）。 */
    public MessageAudited payload() {
        return payload;
    }
}
