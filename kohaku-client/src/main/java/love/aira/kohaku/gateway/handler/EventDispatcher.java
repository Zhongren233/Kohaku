package love.aira.kohaku.gateway.handler;

import java.util.List;
import java.util.function.Consumer;
import love.aira.kohaku.gateway.event.BotEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 有序事件处理链 + 无条件观察者通道：**严格按传入顺序**执行匹配的处理器。
 *
 * <p>两者职责分离：处理链是命令管道（有序、可终止），观察者是广播（无条件、只读）。
 *
 * <ul>
 *   <li>处理器返回 {@link HandlerResult#CONSUMED}：**只终止链内后续处理器**，观察者照常收到事件；</li>
 *   <li>处理器返回 {@link HandlerResult#IGNORED}（或本处理器类型不匹配）：继续下一个；</li>
 *   <li>处理器抛出异常：记 error 日志并按 IGNORED 继续，避免个别处理器异常打断整条链与长连接；</li>
 *   <li>链执行完毕后，{@code observer} <strong>无条件</strong>收到事件 —— 包括被 CONSUMED 的事件
 *       （Spring 下即把事件发布为容器事件，供 {@code @EventListener} 使用）。</li>
 * </ul>
 *
 * <p>观察者在调用线程上同步执行、按事件先后顺序调用，且总在链之后；它不应阻塞（Spring 下即网关读循环线程），
 * 耗时逻辑请自行异步化。
 *
 * <p>顺序由调用方决定：纯 Java 场景即 {@link #EventDispatcher(List, Consumer)} 传入的列表顺序；
 * Spring 场景由 {@code kohaku.qq.handler-order}（Bean 名称列表）优先，其余按 {@code @Order}/{@code Ordered}。
 */
public final class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final List<BotEventHandler<?>> handlers;
    private final Consumer<BotEvent> observer;

    public EventDispatcher(List<BotEventHandler<?>> handlers, Consumer<BotEvent> observer) {
        this.handlers = List.copyOf(handlers);
        this.observer = observer;
    }

    /** 无观察者的事件链。 */
    public EventDispatcher(List<BotEventHandler<?>> handlers) {
        this(handlers, null);
    }

    /** 依次执行处理链，随后无条件通知观察者（被 CONSUMED 的事件同样通知）。 */
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
                break;
            }
        }
        if (observer != null) {
            observer.accept(event);
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
