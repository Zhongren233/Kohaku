package love.aira.kohaku.gateway.event;

import love.aira.kohaku.gateway.event.model.C2cMsgReceive;
import tools.jackson.databind.JsonNode;

/**
 * 单聊主动消息推送被开启，事件名见 TYPE 常量，intent GROUP_AND_C2C_EVENT (1<<25)。
 *
 * <p>强类型事件体见 {@link #payload()}；原始报文体仍可通过 data() 取得。
 */
public final class C2cMsgReceiveEvent extends BotDispatchEvent {

    /** 事件名（网关报文中的 t 字段）。 */
    public static final String TYPE = "C2C_MSG_RECEIVE";

    private final C2cMsgReceive payload;

    public C2cMsgReceiveEvent(long seq, String eventId, JsonNode raw, C2cMsgReceive payload) {
        super(seq, TYPE, eventId, raw);
        this.payload = payload;
    }

    /** 强类型事件体（字段与官方文档「事件体」一致）。 */
    public C2cMsgReceive payload() {
        return payload;
    }
}
