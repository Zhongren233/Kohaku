package love.aira.kohaku.gateway.event;

import love.aira.kohaku.gateway.event.model.InteractionCreate;
import tools.jackson.databind.JsonNode;

/**
 * 互动事件（按钮/菜单/反馈/授权等），事件名见 TYPE 常量，intent INTERACTION (1<<26)。
 *
 * <p>强类型事件体见 {@link #payload()}；原始报文体仍可通过 data() 取得。
 */
public final class InteractionCreateEvent extends BotDispatchEvent {

    /** 事件名（网关报文中的 t 字段）。 */
    public static final String TYPE = "INTERACTION_CREATE";

    private final InteractionCreate payload;

    public InteractionCreateEvent(long seq, JsonNode raw, InteractionCreate payload) {
        super(seq, TYPE, raw);
        this.payload = payload;
    }

    /** 强类型事件体（字段与官方文档「事件体」一致）。 */
    public InteractionCreate payload() {
        return payload;
    }
}
