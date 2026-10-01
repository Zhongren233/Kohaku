package love.aira.kohaku.feature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.gateway.event.model.InteractionResolved;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 互动事件路由：把按钮点击（{@code INTERACTION_CREATE} 且 {@code type=11}）按按钮 data 里的
 * 功能命名空间投递给对应 {@link BotFeature} 的 {@link ButtonHandler}。
 *
 * <ul>
 *   <li>非按钮互动事件（快捷菜单/反馈/授权等）：返回 {@code IGNORED}，交给链上其他处理器；</li>
 *   <li>按钮 data 无法解析、功能或动作未注册：返回 {@code IGNORED}（会继续走链并最终落到 @EventListener）；</li>
 *   <li>命中：返回该按钮处理器的结果（CONSUMED 即终止整条链）。</li>
 * </ul>
 *
 * <p>把它作为 {@code BotEventHandler} Bean 注册即可参与现有有序处理链（Spring 下自动装配）。
 */
public final class InteractionRouter implements BotEventHandler<InteractionCreateEvent> {

    /** 消息按钮回调（INLINE_KEYBOARD）。 */
    public static final int TYPE_INLINE_KEYBOARD = 11;

    private static final Logger log = LoggerFactory.getLogger(InteractionRouter.class);

    private final List<BotFeature> features;
    private final Map<String, Map<String, ButtonHandler>> handlersByFeature;
    private final InteractionResponder responder;

    public InteractionRouter(List<BotFeature> features) {
        this(features, null);
    }

    /**
     * @param responder 互动应答器（Spring 下自动装配为 {@code QqInteractionApi::respond}）；
     *                  为 {@code null} 时不自动应答，需业务自行调用应答接口，否则客户端会 loading 到超时
     */
    public InteractionRouter(List<BotFeature> features, InteractionResponder responder) {
        this.responder = responder;
        this.features = List.copyOf(features);
        Map<String, Map<String, ButtonHandler>> byFeature = new LinkedHashMap<>();
        for (BotFeature feature : this.features) {
            Map<String, ButtonHandler> byAction = new LinkedHashMap<>();
            for (ButtonHandler handler : feature.buttonHandlers()) {
                ButtonHandler previous = byAction.put(handler.action(), handler);
                if (previous != null) {
                    throw new IllegalStateException("功能 " + feature.id() + " 的按钮动作重复: " + handler.action());
                }
            }
            if (byFeature.put(feature.id(), Map.copyOf(byAction)) != null) {
                throw new IllegalStateException("功能 id 重复: " + feature.id());
            }
        }
        this.handlersByFeature = Map.copyOf(byFeature);
    }

    @Override
    public Class<InteractionCreateEvent> eventType() {
        return InteractionCreateEvent.class;
    }

    @Override
    public HandlerResult handle(InteractionCreateEvent event) {
        InteractionCreate payload = event.payload();
        if (payload == null || payload.type() == null || payload.type() != TYPE_INLINE_KEYBOARD) {
            return HandlerResult.IGNORED;
        }
        InteractionResolved resolved = payload.data() == null ? null : payload.data().resolved();
        ButtonData.Parts parts = ButtonData.decode(resolved == null ? null : resolved.buttonData());
        if (parts == null) {
            log.debug("按钮 data 无法解析，按 IGNORED 继续: data={}", resolved == null ? null : resolved.buttonData());
            return HandlerResult.IGNORED;
        }
        ButtonHandler handler = handlersByFeature.getOrDefault(parts.featureId(), Map.of()).get(parts.action());
        if (handler == null) {
            log.debug("未注册的按钮回调: feature={} action={}", parts.featureId(), parts.action());
            return HandlerResult.IGNORED;
        }
        ButtonContext context = new ButtonContext(event, parts.featureId(), parts.action(), parts.state(),
                resolved.buttonId(), resolved.buttonData(), payload.chatType(), payload.scene(),
                payload.userOpenid(), payload.groupOpenid(), payload.groupMemberOpenid());
        log.debug("按钮点击 {}.{} 交给 {} 处理", context.featureId(), context.action(),
                handler.getClass().getSimpleName());
        HandlerResult result;
        try {
            result = handler.onButton(context);
        } catch (RuntimeException e) {
            respond(payload, InteractionResponder.CODE_FAILED);   // 处理失败也要应答，避免客户端一直 loading
            throw e;
        }
        if (result == HandlerResult.CONSUMED) {
            respond(payload, InteractionResponder.CODE_SUCCESS);
        }
        return result;
    }

    /**
     * 应答互动事件（{@code PUT /interactions/{d.id}}）：平台要求 type=11/12 必须回应，否则客户端 loading 到超时。
     * 这里只在「处理成功」与「处理抛异常」时应答；返回 IGNORED 时交给链上后续处理器决定。
     */
    private void respond(InteractionCreate payload, int code) {
        if (responder == null || payload.id() == null) {
            return;
        }
        try {
            responder.respond(payload.id(), code);
        } catch (RuntimeException e) {
            log.warn("应答互动事件失败 interaction_id={}: {}", payload.id(), e.toString());
        }
    }

    /** 已注册的功能（便于诊断与测试）。 */
    public List<BotFeature> features() {
        return List.copyOf(features);
    }
}
