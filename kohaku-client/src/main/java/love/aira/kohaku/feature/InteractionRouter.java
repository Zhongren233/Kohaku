package love.aira.kohaku.feature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
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
 *   <li>按钮 data 无法解析、功能或动作未注册：返回 {@code IGNORED}（继续走链；无论是否被消费，事件都会到达 @EventListener）；</li>
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
    private final InteractionAckMode ackMode;
    private final Executor handlerExecutor;

    public InteractionRouter(List<BotFeature> features) {
        this(features, null, InteractionAckMode.IMMEDIATE, null);
    }

    /**
     * @param responder 互动应答器（Spring 下自动装配为 {@code QqInteractionApi::respond}）；
     *                  为 {@code null} 时不自动应答，需业务自行回应，否则客户端会 loading 到超时
     */
    public InteractionRouter(List<BotFeature> features, InteractionResponder responder) {
        this(features, responder, InteractionAckMode.IMMEDIATE, null);
    }

    public InteractionRouter(List<BotFeature> features, InteractionResponder responder, InteractionAckMode ackMode) {
        this(features, responder, ackMode, null);
    }

    /**
     * @param handlerExecutor 按钮处理器的执行器：非空时在 {@link InteractionAckMode#IMMEDIATE} 模式下，
     *                        应答后立即把处理逻辑交给它异步执行，避免耗时逻辑顶住网关读循环
     *                        （Spring 下默认使用 {@code kohakuHandlerExecutor}）；为空则同步执行
     */
    public InteractionRouter(List<BotFeature> features, InteractionResponder responder, InteractionAckMode ackMode,
                             Executor handlerExecutor) {
        this.responder = responder;
        this.ackMode = ackMode == null ? InteractionAckMode.IMMEDIATE : ackMode;
        this.handlerExecutor = handlerExecutor;
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
        if (ackMode == InteractionAckMode.IMMEDIATE) {
            respond(payload, InteractionResponder.CODE_SUCCESS);   // 先应答，客户端立即结束 loading
            if (handlerExecutor != null) {
                // 应答后再把耗时逻辑丢到工作线程，避免顶住网关读循环（心跳/其它事件不受影响）
                handlerExecutor.execute(() -> runHandler(handler, context, payload));
                return HandlerResult.CONSUMED;
            }
        }
        HandlerResult result;
        try {
            result = handler.onButton(context);
        } catch (RuntimeException e) {
            if (ackMode == InteractionAckMode.AFTER_HANDLING) {
                respond(payload, InteractionResponder.CODE_FAILED);
            } else {
                log.warn("已提前应答成功，但按钮处理失败 interaction_id={}: {}", payload.id(), e.toString());
            }
            throw e;
        }
        if (ackMode == InteractionAckMode.AFTER_HANDLING && result == HandlerResult.CONSUMED) {
            respond(payload, InteractionResponder.CODE_SUCCESS);
        }
        return result;
    }

    /** 异步执行处理器：异常只记录（应答已完成，没有链条需要传播）。 */
    private void runHandler(ButtonHandler handler, ButtonContext context, InteractionCreate payload) {
        try {
            handler.onButton(context);
        } catch (RuntimeException e) {
            log.error("按钮处理失败 interaction_id={} feature={} action={}", payload.id(), context.featureId(),
                    context.action(), e);
        }
    }

    /**
     * 应答互动事件（{@code PUT /interactions/{d.id}}）：平台要求 type=11/12 必须回应，否则客户端 loading 到超时。
     *
     * <p>只对「命中处理器」的点击应答：{@link InteractionAckMode#IMMEDIATE} 在调用处理器前应答成功；
     * {@link InteractionAckMode#AFTER_HANDLING} 在处理器返回 CONSUMED 时应答成功、抛异常时应答失败。
     * 命中但返回 {@code IGNORED}（或未命中任何处理器）不应答，交由链上后续处理器决定。
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
