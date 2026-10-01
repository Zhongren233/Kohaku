package love.aira.kohaku.feature;

import java.util.Objects;
import java.util.function.Function;
import love.aira.kohaku.gateway.handler.HandlerResult;

/**
 * 功能模块内的按钮回调：把某次按钮点击交给"产生这个按钮的功能"处理。
 *
 * <p>同一个功能下可以有多个动作（如 {@code next}/{@code prev}/{@code page}），按 action 精确匹配；
 * 返回 {@link HandlerResult#CONSUMED} 表示已处理，返回 {@code IGNORED} 则继续走事件处理链。
 */
public interface ButtonHandler {

    /** 动作名，与按钮 data 中的 action 段一致。 */
    String action();

    /** 处理点击；可用 {@link love.aira.kohaku.reply.BotReplies} 直接做被动回复。 */
    HandlerResult onButton(ButtonContext context);

    /**
     * 函数式构造：
     *
     * <pre>{@code
     * ButtonHandler.of("next", context -> {
     *     replies.send(context.interaction(), page(context.intState("p", 1) + 1));
     *     return HandlerResult.CONSUMED;
     * });
     * }</pre>
     */
    static ButtonHandler of(String action, Function<ButtonContext, HandlerResult> handler) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(handler, "handler");
        return new ButtonHandler() {
            @Override
            public String action() {
                return action;
            }

            @Override
            public HandlerResult onButton(ButtonContext context) {
                return handler.apply(context);
            }
        };
    }
}
