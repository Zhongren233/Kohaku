package love.aira.kohaku.gateway.handler;

import java.util.Objects;
import java.util.function.Function;
import love.aira.kohaku.gateway.event.BotEvent;

/**
 * 事件处理器：按声明顺序依次处理同一事件，返回 {@link HandlerResult#CONSUMED} 即终止后续处理器。
 *
 * <p>类型匹配按 {@link #eventType()} 的实例关系（{@code isInstance}），因此注册 {@code BotDispatchEvent}
 * 或 {@code BotEvent} 的处理器可以收到全部/所有派发事件（兜底），注册具体事件类型则只收该类型及其子类。
 *
 * @param <E> 处理的事件类型
 */
public interface BotEventHandler<E extends BotEvent> {

    /** 本处理器处理的事件类型。 */
    Class<E> eventType();

    /** 处理事件。 */
    HandlerResult handle(E event);

    /**
     * 函数式构造：不必再写 {@link #eventType()}（类型由参数给定，不做反射推断）。
     *
     * <pre>{@code
     * BotEventHandler<C2cMessageCreateEvent> echo = BotEventHandler.of(C2cMessageCreateEvent.class, event -> {
     *     replies.text(event, "echo: " + event.payload().content());
     *     return HandlerResult.CONSUMED;
     * });
     * }</pre>
     */
    static <E extends BotEvent> BotEventHandler<E> of(Class<E> eventType, Function<E, HandlerResult> handler) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(handler, "handler");
        return new BotEventHandler<>() {
            @Override
            public Class<E> eventType() {
                return eventType;
            }

            @Override
            public HandlerResult handle(E event) {
                return handler.apply(event);
            }
        };
    }
}
