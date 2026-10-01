package love.aira.kohaku.feature;

import java.util.Map;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;

/**
 * 一次按钮点击的上下文（已解析出功能名、动作与自描述状态）。
 *
 * @param interaction        强类型互动事件（原始报文可用 {@code interaction.data()} 取）
 * @param featureId          按钮所属功能，如 {@code card}
 * @param action             动作名，如 {@code next}
 * @param state              自描述状态（来自按钮 data，已解码）
 * @param buttonId           平台回传的按钮 id
 * @param buttonData         平台回传的原始 data
 * @param chatType           聊天场景：0=频道 1=群聊 2=单聊
 * @param scene              场景字符串：guild/group/c2c
 * @param userOpenid         单聊用户 OpenID（单聊场景）
 * @param groupOpenid        群 OpenID（群聊场景）
 * @param groupMemberOpenid  群成员 OpenID（群聊场景）
 */
public record ButtonContext(
        InteractionCreateEvent interaction,
        String featureId,
        String action,
        Map<String, String> state,
        String buttonId,
        String buttonData,
        Integer chatType,
        String scene,
        String userOpenid,
        String groupOpenid,
        String groupMemberOpenid) {

    public ButtonContext {
        state = state == null ? Map.of() : Map.copyOf(state);
    }

    /**
     * 被动回复用的 {@code event_id} —— 取**事件最外层 id**（形如 {@code INTERACTION_CREATE:963df69c-…}）。
     * 实测：用事件体 {@code d.id} 会被平台以 40034025「请求参数event_id无效」拒绝。
     */
    public String eventId() {
        return interaction.eventId();
    }

    /**
     * 应答互动用的 {@code interaction_id} —— 取事件体 {@code d.id}（**不带** {@code INTERACTION_CREATE:} 前缀）。
     * 两个 id 用途不同：回复消息用 {@link #eventId()}，调 {@code PUT /interactions/{id}} 用本方法。
     */
    public String interactionId() {
        return interaction.payload() == null ? null : interaction.payload().id();
    }

    public boolean isGuild() {
        return chatType != null && chatType == 0;
    }

    public boolean isGroup() {
        return chatType != null && chatType == 1;
    }

    public boolean isC2c() {
        return chatType != null && chatType == 2;
    }

    public String state(String key) {
        return state.get(key);
    }

    public int intState(String key, int defaultValue) {
        String value = state.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 便捷：把消息包装成对该互动事件的被动回复。 */
    public SendMessageRequest replyingTo(SendMessageRequest request) {
        return request.replyingToEvent(eventId());
    }
}
