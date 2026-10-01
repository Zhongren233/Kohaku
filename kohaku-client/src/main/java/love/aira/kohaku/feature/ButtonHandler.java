package love.aira.kohaku.feature;

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

    /** 处理点击；可用 {@link InteractionReplies} 直接做被动回复。 */
    HandlerResult onButton(ButtonContext context);
}
