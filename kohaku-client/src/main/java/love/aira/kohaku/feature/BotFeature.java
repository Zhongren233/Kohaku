package love.aira.kohaku.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;

/**
 * 功能模块：把「消息入口 + 自己的按钮回调」打包成一个可路由单元。
 *
 * <p>按钮 data 以 {@code featureId} 作为命名空间（见 {@link ButtonData}），点击后由
 * {@link InteractionRouter} 投递给本功能的 {@link #buttonHandlers()}，因此翻页之类的交互会回到
 * 产生按钮的功能，而不是落到别的消息处理器。
 *
 * <p>两种写法等价，按复杂度选：
 * <ol>
 *   <li><b>实现本接口</b>（功能有多个处理器、需要字段/复用逻辑时）；</li>
 *   <li><b>函数式装配</b>{@link #of(String)}：id + 若干 lambda 就是完整功能，连类都不用写——
 *       适合"一个命令 + 两三个按钮"这种主体逻辑本来就很短的功能。</li>
 * </ol>
 *
 * <pre>{@code
 * @Bean
 * BotFeature cardFeature(BotReplies replies) {
 *     return BotFeature.of("card")
 *             .message(C2cMessageCreateEvent.class, event -> entry(event, replies))
 *             .button("next", context -> {
 *                 replies.send(context.interaction(), page(context.intState("p", 1) + 1));
 *                 return HandlerResult.CONSUMED;
 *             })
 *             .build();
 * }
 * }</pre>
 */
public interface BotFeature {

    /** 功能标识，同时是按钮 data 的命名空间；只允许字母数字与 {@code _.-}。 */
    String id();

    /**
     * 本功能的消息入口处理器（可与其它 Bean 一起进入同一条有序处理链）。
     *
     * <p>只做命令、没有按钮的功能不需要实现本方法。
     */
    default List<BotEventHandler<?>> messageHandlers() {
        return List.of();
    }

    /**
     * 本功能的按钮回调，按 action 匹配；**没有按钮的功能返回空列表即可**（默认值）。
     *
     * <p>本功能的 {@link #id()} 会作为按钮 data 的命名空间（见 {@link ButtonData}），
     * 点击后由 {@link InteractionRouter} 投递到这里注册的 {@link ButtonHandler}。
     */
    default List<ButtonHandler> buttonHandlers() {
        return List.of();
    }

    /** 函数式装配入口：{@code BotFeature.of("card").message(...).button(...).build()}。 */
    static Builder of(String id) {
        return new Builder(id);
    }

    /** 链式构造器：{@link #build()} 之前可变，产出的是不可变快照（之后再加也影响不到已产出的功能）。 */
    final class Builder {

        private final String id;
        private final List<BotEventHandler<?>> messageHandlers = new ArrayList<>();
        private final List<ButtonHandler> buttonHandlers = new ArrayList<>();

        private Builder(String id) {
            Objects.requireNonNull(id, "id");
            ButtonData.requireSimple("featureId", id);   // 启动即校验，别等到第一次点击才报错
            this.id = id;
        }

        /** 消息入口：类型由参数给定，处理器返回 {@link HandlerResult}（不属于自己的输入返回 IGNORED）。 */
        public <E extends BotEvent> Builder message(Class<E> eventType, Function<E, HandlerResult> handler) {
            messageHandlers.add(BotEventHandler.of(eventType, handler));
            return this;
        }

        /** 按钮回调：action 与按钮 data 中的动作段一致。 */
        public Builder button(String action, Function<ButtonContext, HandlerResult> handler) {
            ButtonData.requireSimple("action", action);
            buttonHandlers.add(ButtonHandler.of(action, handler));
            return this;
        }

        public BotFeature build() {
            return new BuiltFeature(id, List.copyOf(messageHandlers), List.copyOf(buttonHandlers));
        }

        private record BuiltFeature(String id, List<BotEventHandler<?>> messageHandlers,
                                    List<ButtonHandler> buttonHandlers) implements BotFeature {
        }
    }
}
