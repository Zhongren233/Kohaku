package love.aira.kohaku.feature;

import java.util.Map;
import java.util.Objects;
import love.aira.kohaku.gateway.event.BotDispatchEvent;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupMessageCreateEvent;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.gateway.event.model.MessageAuthor;

/**
 * 功能收到一次输入时的上下文：触发事件 + 状态 + 已解析的身份，按钮点击还带 {@link Button} 专有信息。
 *
 * <ul>
 *   <li><b>消息入口</b>：{@link #event()} 是触发命令的那条消息事件（单聊 / 群 @ / 群全量）；</li>
 *   <li><b>按钮回调</b>：{@link #event()} 是本次互动事件，{@link #button()} 给出功能名/动作/按钮原始数据。</li>
 * </ul>
 *
 * <p>按钮路径**没有**当初产生按钮的那条消息事件：平台回传的只有本次点击，状态是自描述地存在按钮 data 里的。
 * 需要「是谁点的 / 在哪个群」就读 {@link #userOpenid()} / {@link #groupOpenid()}。
 *
 * <p>{@link #userOpenid()} 在**所有场景**都有值：单聊取 {@code user_openid}、群聊取
 * {@code group_member_openid}（群成员标识）—— 同一个用户是同一个值，不要按场景分支；
 * {@link #groupOpenid()} 只在群聊场景非空。取值规则与 {@link love.aira.kohaku.reply.BotReplies}
 * 选被动回复目标时一致。
 *
 * @param event       触发本次输入的事件：消息事件或互动事件
 * @param state       状态（按钮路径来自按钮 data；入口路径来自命令参数解析）
 * @param scene       场景字符串：c2c/group/guild
 * @param chatType    聊天场景：0=频道 1=群聊 2=单聊
 * @param userOpenid  触发者 OpenID（各场景都有值）
 * @param groupOpenid 群 OpenID（仅群聊场景非空）
 * @param button      按钮点击专有信息；消息入口路径为 {@code null}
 */
public record FeatureContext(
        BotEvent event,
        Map<String, String> state,
        String scene,
        Integer chatType,
        String userOpenid,
        String groupOpenid,
        Button button) {

    /**
     * 按钮点击专有信息。
     *
     * @param interaction 本次互动事件（被动回复、取原始报文用）
     * @param featureId   按钮所属功能（按钮 data 的命名空间）
     * @param action      动作名
     * @param buttonId    平台回传的按钮 id
     * @param buttonData  平台回传的原始 data
     */
    public record Button(InteractionCreateEvent interaction, String featureId, String action, String buttonId,
                         String buttonData) {
    }

    public FeatureContext {
        Objects.requireNonNull(event, "event");
        state = state == null ? Map.of() : Map.copyOf(state);
    }

    /**
     * 消息入口的上下文：身份取自事件体。
     *
     * @throws IllegalArgumentException 事件不是消息入口会收到的三种消息事件之一
     */
    public static FeatureContext ofMessage(BotEvent event, Map<String, String> state) {
        return switch (event) {
            case C2cMessageCreateEvent c2c -> {
                C2cMessage message = c2c.payload();
                yield new FeatureContext(event, state, "c2c", 2,
                        senderOpenid(message == null ? null : message.author(), false), null, null);
            }
            case GroupAtMessageCreateEvent group -> ofGroup(event, state, group.payload());
            case GroupMessageCreateEvent group -> ofGroup(event, state, group.payload());
            default -> throw new IllegalArgumentException("不支持的事件类型（消息入口只收三种消息事件）: "
                    + event.getClass().getName());
        };
    }

    /** 按钮回调的上下文：身份与场景取自互动事件体，按钮信息由路由给出。 */
    public static FeatureContext ofButton(InteractionCreateEvent event, String featureId, String action,
                                          Map<String, String> state, String buttonId, String buttonData) {
        InteractionCreate payload = event.payload();
        return new FeatureContext(event, state,
                payload == null ? null : payload.scene(),
                payload == null ? null : payload.chatType(),
                payload == null ? null : senderOpenid(payload.userOpenid(), payload.groupMemberOpenid()),
                payload == null ? null : payload.groupOpenid(),
                new Button(event, featureId, action, buttonId, buttonData));
    }

    private static FeatureContext ofGroup(BotEvent event, Map<String, String> state, GroupMessage message) {
        return new FeatureContext(event, state, "group", 1,
                senderOpenid(message == null ? null : message.author(), true),
                message == null ? null : message.groupOpenid(), null);
    }

    /**
     * 触发者 OpenID：平台上单聊下发 {@code user_openid}、群聊下发 {@code member_openid}，同一个用户是
     * 同一个值 —— 统一成一个字段，业务侧不必按场景分支（首选本场景该有的字段，另一个兜底）。
     */
    private static String senderOpenid(MessageAuthor author, boolean group) {
        if (author == null) {
            return null;
        }
        return group
                ? firstNonBlank(author.memberOpenid(), author.userOpenid())
                : firstNonBlank(author.userOpenid(), author.memberOpenid());
    }

    private static String senderOpenid(String userOpenid, String groupMemberOpenid) {
        return firstNonBlank(userOpenid, groupMemberOpenid);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    /** 读取状态值，缺失时返回 {@code null}。 */
    public String state(String key) {
        return state.get(key);
    }

    /** 读取整型状态，缺失或非法时返回默认值。 */
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

    /** 是否是按钮点击（消息入口路径为 {@code false}）。 */
    public boolean isButton() {
        return button != null;
    }

    /** 本次输入对应的事件是互动事件时返回它，否则返回 {@code null}。 */
    public InteractionCreateEvent interaction() {
        return event instanceof InteractionCreateEvent interaction ? interaction : null;
    }

    public boolean isC2c() {
        return chatType != null && chatType == 2;
    }

    public boolean isGroup() {
        return chatType != null && chatType == 1;
    }

    public boolean isGuild() {
        return chatType != null && chatType == 0;
    }

    /**
     * 平台下发的事件 id（形如 {@code INTERACTION_CREATE:963df69c-…}）。互动事件的被动回复需要它
     * （{@code event_id}）；消息事件用 {@code msg_id}，由 {@link love.aira.kohaku.reply.BotReplies} 自动补。
     */
    public String eventId() {
        return event instanceof BotDispatchEvent dispatch ? dispatch.eventId() : null;
    }
}
