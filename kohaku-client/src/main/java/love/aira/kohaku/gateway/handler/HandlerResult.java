package love.aira.kohaku.gateway.handler;

import java.util.Objects;

/**
 * 事件处理器的处理结果：**同步的路由判定** + **可选的异步副作用**。
 *
 * <p>路由判定同步——处理链据返回值决定是否继续；副作用可异步——{@link #consumed(Runnable)} 返回的结果
 * 会终止链内后续处理器，并把动作交给框架的 worker 线程池执行（{@link EventDispatcher} 构造时传入的
 * {@code Executor}；Spring 下即 {@code kohakuHandlerExecutor}）。因此处理器里的耗时逻辑
 * （DB / LLM / 慢 REST）不必再自己管线程池：
 *
 * <pre>{@code
 * .message(GroupAtMessageCreateEvent.class, event -> {
 *     if (!isMine(event)) {
 *         return HandlerResult.IGNORED;                          // 同步路由：让给后面的处理器
 *     }
 *     return HandlerResult.consumed(() ->                        // 异步副作用：框架丢线程池
 *             replies.markdown(event, render(), keyboard()));
 * })
 * }</pre>
 *
 * <p>{@link #CONSUMED}/{@link #IGNORED} 是单例常量（可用 {@code ==} 比较）；异步结果用
 * {@link #isConsumed()} 判定。异步动作默认在单线程池上按提交顺序执行，因此默认仍保持事件顺序；
 * 把线程池调成多线程则顺序不再保证。
 *
 * <p><b>兼容性说明</b>：本类由 {@code enum} 改为 {@code final class}（0.2.x 的有意破坏性变更），
 * 常量用法与 {@code ==} 语义保持不变，但不能再用于 {@code switch} 或依赖枚举特性。
 */
public final class HandlerResult {

    /** 已消费该事件（同步、无异步副作用）：终止链内后续处理器；观察者通道不受影响。 */
    public static final HandlerResult CONSUMED = new HandlerResult(true, null);

    /** 忽略该事件：继续执行下一个处理器；观察者通道不受影响。 */
    public static final HandlerResult IGNORED = new HandlerResult(false, null);

    private final boolean consumed;
    private final Runnable after;

    private HandlerResult(boolean consumed, Runnable after) {
        this.consumed = consumed;
        this.after = after;
    }

    /**
     * 消费该事件，并让框架在 worker 线程池上执行 {@code after} —— 耗时逻辑放这里。
     *
     * @param after 异步副作用，不能为 {@code null}
     */
    public static HandlerResult consumed(Runnable after) {
        return new HandlerResult(true, Objects.requireNonNull(after, "after"));
    }

    /** 是否消费了该事件：{@link #CONSUMED} 与 {@link #consumed(Runnable)} 均为 {@code true}。 */
    public boolean isConsumed() {
        return consumed;
    }

    /** 需要异步执行的副作用；同步结果（{@link #CONSUMED}/{@link #IGNORED}）返回 {@code null}。 */
    public Runnable after() {
        return after;
    }

    @Override
    public String toString() {
        if (after != null) {
            return "CONSUMED(async)";
        }
        return consumed ? "CONSUMED" : "IGNORED";
    }
}
