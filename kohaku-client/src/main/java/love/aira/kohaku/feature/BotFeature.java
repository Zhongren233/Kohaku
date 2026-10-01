package love.aira.kohaku.feature;

import java.util.List;
import love.aira.kohaku.gateway.handler.BotEventHandler;

/**
 * 功能模块：把「消息入口 + 自己的按钮回调」打包成一个可路由单元。
 *
 * <p>按钮 data 以 {@code featureId} 作为命名空间（见 {@link ButtonData}），点击后由
 * {@link InteractionRouter} 投递给本功能的 {@link #buttonHandlers()}，因此翻页之类的交互会回到
 * 产生按钮的功能，而不是落到别的消息处理器。
 */
public interface BotFeature {

    /** 功能标识，同时是按钮 data 的命名空间；只允许字母数字与 {@code _.-}。 */
    String id();

    /** 本功能的消息入口处理器（可与其它 Bean 一起进入同一条有序处理链）。 */
    default List<BotEventHandler<?>> messageHandlers() {
        return List.of();
    }

    /** 本功能的按钮回调，按 action 匹配。 */
    List<ButtonHandler> buttonHandlers();
}
