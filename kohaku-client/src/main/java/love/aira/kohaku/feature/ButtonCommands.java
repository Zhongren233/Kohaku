package love.aira.kohaku.feature;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import love.aira.kohaku.interaction.ButtonData;
import java.util.Set;
import java.util.function.Function;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.support.CommandArgs;
import love.aira.kohaku.support.Mentions;

/**
 * 「命令进入 + 状态驱动按钮」骨架：最常用的一种功能形态（如分页卡片、菜单、列表），
 * 业务只需提供两个纯函数，管道由框架补齐。
 *
 * <pre>{@code
 * @Bean
 * BotFeature card(BotReplies replies) {
 *     return ButtonCommands.of("card")                            // 默认入口命令 = /card
 *             .state(CardFeature::state)                          // 命令参数 → 状态；null 表示参数不成立
 *             .render(CardFeature::page)                          // 上下文（状态+事件+身份）→ 消息，两条路径共用
 *             .build(replies);                                    // 默认动作 next/prev/page
 * }
 * }</pre>
 *
 * <p>框架补齐的部分：
 * <ul>
 *   <li>入口：单聊、群内 @机器人、群内全量消息各注册一个入口（全量需平台为该群开通「接收全量信息」，
 *       否则该事件不会下发），按命令前缀匹配，不匹配一律 {@code IGNORED}（不会吃掉别人的命令），
 *       命中则用 {@code render(状态)} 被动回复；匹配前会剥掉行首的机器人提及
 *       （全量消息的 content 保留 {@code <@id>}），因此群里 {@code @机器人 /card} 与 {@code /card} 等效；</li>
 *   <li>按钮：每个动作一个回调，**从按钮 data 的 state 里取状态**再渲染 —— 状态自描述，
 *       所以"点第 3 页"与"发 /card next 3"走同一条渲染路径，重复点击/重放天然幂等；</li>
 *   <li>渲染能拿到触发事件与身份：{@code render} 收到的是 {@link FeatureContext} —— 入口路径是那条消息
 *       事件，按钮路径是本次互动事件，触发者/群直接读 {@code context.userOpenid()}（各场景都有值） /
 *       {@code groupOpenid()}（仅群聊非空）；</li>
 *   <li>被动回复走 {@link BotReplies}（目标、msg_id/msg_seq、互动 event_id、超限告警全自动）；
 *       回复是耗时上行，框架以 {@link HandlerResult#consumed(Runnable)} 交给 worker 线程池异步执行，
 *       因此 {@code state}/{@code render} 应当是快速的纯函数。</li>
 * </ul>
 *
 * <p>状态即按钮 data 的 state 段（{@link ButtonData}），只承载渲染所需的键值（如页码），不要塞大对象；
 * 身份（发送者/群）与触发事件不要在状态里传 —— 状态会写进按钮 data 发给客户端，而
 * {@link FeatureContext} 已经把它们交给 {@code render} 了。键盘怎么画、状态含义是什么属于业务，
 * 见示例 {@code CardKeyboards}。
 */
public final class ButtonCommands {

    /** 默认按钮动作：与 {@code CardKeyboards} 那类翻页键盘的 action 段一致。 */
    private static final List<String> DEFAULT_ACTIONS = List.of("next", "prev", "page");

    private ButtonCommands() {
    }

    public static Builder of(String featureId) {
        return new Builder(featureId);
    }

    public static final class Builder {

        private final String featureId;
        private final Set<String> commands = new LinkedHashSet<>();
        private final List<String> actions = new ArrayList<>(DEFAULT_ACTIONS);
        private Function<CommandArgs, Map<String, String>> stateParser;
        private Function<FeatureContext, SendMessageRequest> renderer;

        private Builder(String featureId) {
            Objects.requireNonNull(featureId, "featureId");
            ButtonData.requireSimple("featureId", featureId);   // 装配期校验，别等首次点击
            this.featureId = featureId;
        }

        /**
         * 入口命令词（默认 {@code /<featureId>}）：与消息的**第一个分词**精确相等才算命中。
         *
         * <p>命令词必须是单个词（不含空白）；命中后框架把剩余参数交给 {@link #state(Function)} 的解析器。
         */
        public Builder commands(String... prefixes) {
            commands.clear();
            for (String prefix : prefixes) {
                String command = prefix == null ? null : prefix.trim();
                if (command == null || command.isEmpty()) {
                    throw new IllegalArgumentException("命令前缀不能为空");
                }
                if (command.chars().anyMatch(Character::isWhitespace)) {
                    throw new IllegalArgumentException("命令前缀不能包含空白: " + command);
                }
                commands.add(command);
            }
            if (commands.isEmpty()) {
                throw new IllegalArgumentException("至少要有一个命令前缀");
            }
            return this;
        }

        /**
         * 命令参数 → 按钮状态（{@link ButtonData} 的 state 键值）。
         *
         * <p>命令词已由框架匹配并剥离，这里只处理 {@link CommandArgs} 中的参数；返回 {@code null} 或空 Map
         * 表示参数不构成有效状态（按 IGNORED 处理）。
         */
        public Builder state(Function<CommandArgs, Map<String, String>> parser) {
            this.stateParser = Objects.requireNonNull(parser, "state");
            return this;
        }

        /** 状态驱动的按钮动作（默认 {@code next/prev/page}）。action 会和按钮 data 里的动作段精确匹配。 */
        public Builder actions(String... actions) {
            this.actions.clear();
            for (String action : actions) {
                ButtonData.requireSimple("action", action);
                this.actions.add(action);
            }
            if (this.actions.isEmpty()) {
                throw new IllegalArgumentException("至少要有一个按钮动作");
            }
            return this;
        }

        /** 上下文 → 消息：入口与按钮回调**共用**这一个函数，保证两条路径渲染一致。
         *
         * <p>触发事件与身份（发送者、群）在 {@link FeatureContext} 上：入口路径是那条消息事件，
         * 按钮路径是本次互动事件。状态本身只承载渲染所需的键值（如页码）——身份不要塞进状态，
         * 那会写进按钮 data 发给客户端。
         */
        public Builder render(Function<FeatureContext, SendMessageRequest> renderer) {
            this.renderer = Objects.requireNonNull(renderer, "render");
            return this;
        }

        public BotFeature build(BotReplies replies) {
            Objects.requireNonNull(replies, "replies");
            Objects.requireNonNull(stateParser, "state（命令参数 → 状态）");
            Objects.requireNonNull(renderer, "render（上下文 → 消息）");
            if (commands.isEmpty()) {
                commands.add("/" + featureId);
            }
            List<String> buttonActions = List.copyOf(actions);

            BotFeature.Builder feature = BotFeature.of(featureId)
                    .message(C2cMessageCreateEvent.class,
                            event -> entry(event, event.payload() == null ? null : event.payload().content(), replies))
                    .message(GroupAtMessageCreateEvent.class,
                            event -> entry(event, commandText(event.payload()), replies))
                    .message(GroupMessageCreateEvent.class,
                            event -> entry(event, commandText(event.payload()), replies));
            for (String action : buttonActions) {
                feature.button(action, context -> {
                    SendMessageRequest request = render(context);   // 渲染是纯函数，保持同步
                    return HandlerResult.consumed(() -> replies.send(context.interaction(), request));
                });
            }
            return feature.build();
        }

        /**
         * 命令文本：群消息先剥掉行首的机器人提及 —— 群全量消息（{@code GROUP_MESSAGE_CREATE}）的 content
         * 会原样保留 {@code <@机器人id>}，而群 @ 消息的平台已剥好。单聊没有提及。
         */
        private static String commandText(GroupMessage message) {
            return message == null ? null : Mentions.stripLeading(message.content(), message.mentions());
        }

        private HandlerResult entry(BotEvent event, String content, BotReplies replies) {
            CommandArgs command = CommandArgs.parse(content);
            if (command == null || !commands.contains(command.command())) {
                return HandlerResult.IGNORED;   // 不是本功能的命令，交给后面的处理器
            }
            Map<String, String> state = stateParser.apply(command);
            if (state == null || state.isEmpty()) {
                return HandlerResult.IGNORED;
            }
            SendMessageRequest request = render(FeatureContext.ofMessage(event, state));
            return HandlerResult.consumed(() -> replies.send(event, request));
        }

        private SendMessageRequest render(FeatureContext context) {
            SendMessageRequest request = renderer.apply(context);
            if (request == null) {
                throw new IllegalStateException("render 返回了 null：状态 " + context.state()
                        + "（功能 " + featureId + "）");
            }
            return request;
        }
    }
}
