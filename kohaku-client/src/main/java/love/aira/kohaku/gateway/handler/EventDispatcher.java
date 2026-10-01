package love.aira.kohaku.gateway.handler;

import java.util.List;
import java.util.function.Consumer;
import love.aira.kohaku.gateway.event.BotEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 有序事件处理链：**严格按传入顺序**执行匹配的处理器。
 *
 * <ul>
 *   <li>处理器返回 {@link HandlerResult#CONSUMED}：立即停止，后续处理器与兜底消费者都不再执行；</li>
 *   <li>处理器返回 {@link HandlerResult#IGNORED}（或本处理器类型不匹配）：继续下一个；</li>
 *   <li>处理器抛出异常：记 error 日志并按 IGNORED 继续，避免个别处理器异常打断整条链与长连接；</li>
 *   <li>所有处理器都未消费：交给兜底消费者（Spring 下即把事件发布为容器事件，供 {@code @EventListener} 使用）。</li>
 * </ul>
 *
 * <p>顺序由调用方决定：纯 Java 场景即 {@link #EventDispatcher(List, Consumer)} 传入的列表顺序；
 * Spring 场景由 {@code kohaku.qq.handler-order}（Bean 名称列表）优先，其余按 {@code @Order}/{@code Ordered}。
 */
public final class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final List<BotEventHandler<?>> handlers;
    private final Consumer<BotEvent> fallback;

    public EventDispatcher(List<BotEventHandler<?>> handlers, Consumer<BotEvent> fallback) {
        this.handlers = List.copyOf(handlers);
        this.fallback = fallback;
    }

    /** 无兜底消费者的事件链。 */
    public EventDispatcher(List<BotEventHandler<?>> handlers) {
        this(handlers, null);
    }

    /** 依次执行；被消费则终止，否则最后交给兜底消费者。 */
    public void dispatch(BotEvent event) {
        for (BotEventHandler<?> handler : handlers) {
            if (!handler.eventType().isInstance(event)) {
                continue;
            }
            HandlerResult result;
            try {
                result = handle(handler, event);
            } catch (RuntimeException e) {
                log.error("event handler {} failed on {}, continuing as IGNORED",
                        handler.getClass().getName(), event.getClass().getSimpleName(), e);
                continue;
            }
            if (result == HandlerResult.CONSUMED) {
                log.debug("event {} consumed by {}", event.getClass().getSimpleName(), handler.getClass().getName());
                return;
            }
        }
        if (fallback != null) {
            fallback.accept(event);
        }
    }

    /** 参与本次分发的处理器（按执行顺序）。 */
    public List<BotEventHandler<?>> handlers() {
        return handlers;
    }

    @SuppressWarnings("unchecked")
    private static HandlerResult handle(BotEventHandler<?> handler, BotEvent event) {
        return ((BotEventHandler<BotEvent>) handler).handle(event);
    }
}
