package love.aira.kohaku.gateway.handler;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
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
 *   <li>处理器返回「已消费」（{@link HandlerResult#CONSUMED} 或 {@link HandlerResult#consumed(Runnable)}）：
 *       **只终止链内后续处理器**，观察者照常收到事件；后者携带的副作用会交给 worker 线程池异步执行；</li>
 *   <li>处理器返回 {@link HandlerResult#IGNORED}（或本处理器类型不匹配）：继续下一个；</li>
 *   <li>处理器抛出异常：记 error 日志并按 IGNORED 继续，避免个别处理器异常打断整条链与长连接；</li>
 *   <li>链执行完毕后，{@code observer} <strong>无条件</strong>收到事件 —— 包括被 CONSUMED 的事件
 *       （Spring 下即把事件发布为容器事件，供 {@code @EventListener} 使用）。</li>
 * </ul>
 *
 * <p>处理链与观察者都**在调用 {@code dispatch} 的线程上同步执行**，按事件先后顺序调用；观察者总在链之后。
 * 在 Spring starter 中，该线程是网关专用的单线程派发器（{@code qq-gateway-dispatch}），不是网络读循环，
 * 因此慢 handler 不会拖垮长连接。
 *
 * <p>要避免一个慢 handler 串行拖住后续事件，处理器可返回 {@link HandlerResult#consumed(Runnable)}，把耗时
 * 逻辑交给构造时传入的 {@code afterExecutor} 异步执行（Spring 下即 {@code kohakuHandlerExecutor}）；
 * 执行器为单线程时按提交顺序执行、仍保持事件顺序，多线程则不再保证。
 *
 * <p>顺序由调用方决定：纯 Java 场景即 {@link #EventDispatcher(List, Consumer)} 传入的列表顺序；
 * Spring 场景由 {@code kohaku.qq.handler-order}（Bean 名称列表）优先，其余按 {@code @Order}/{@code Ordered}。
 */
public final class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final List<BotEventHandler<?>> handlers;
    private final Consumer<BotEvent> observer;
    private final Executor afterExecutor;

    /**
     * @param afterExecutor 执行 {@link HandlerResult#consumed(Runnable)} 副作用的工作线程池；
     *                      为 {@code null} 时在当前线程同步执行（退化为无异步能力）
     */
    public EventDispatcher(List<BotEventHandler<?>> handlers, Consumer<BotEvent> observer, Executor afterExecutor) {
        this.handlers = List.copyOf(handlers);
        this.observer = observer;
        this.afterExecutor = afterExecutor;
    }

    /** 无异步执行器的处理链：异步副作用退化为当前线程同步执行。 */
    public EventDispatcher(List<BotEventHandler<?>> handlers, Consumer<BotEvent> observer) {
        this(handlers, observer, null);
    }

    /** 无观察者的事件链。 */
    public EventDispatcher(List<BotEventHandler<?>> handlers) {
        this(handlers, null, null);
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
            if (result.isConsumed()) {
                log.debug("event {} consumed by {}", event.getClass().getSimpleName(), handler.getClass().getName());
                runAfter(result, event);
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

    /** 把消费结果的异步副作用交给 worker 线程池；无执行器时在当前线程执行。 */
    private void runAfter(HandlerResult result, BotEvent event) {
        Runnable after = result.after();
        if (after == null) {
            return;
        }
        if (afterExecutor == null) {
            runSafely(after, event);
            return;
        }
        try {
            afterExecutor.execute(() -> runSafely(after, event));
        } catch (RejectedExecutionException e) {
            log.warn("worker 线程池已关闭，丢弃 {} 的异步副作用", event.getClass().getSimpleName());
        }
    }

    private static void runSafely(Runnable after, BotEvent event) {
        try {
            after.run();
        } catch (RuntimeException e) {
            log.error("处理器的异步副作用执行失败 for {}", event.getClass().getSimpleName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static HandlerResult handle(BotEventHandler<?> handler, BotEvent event) {
        return ((BotEventHandler<BotEvent>) handler).handle(event);
    }
}
