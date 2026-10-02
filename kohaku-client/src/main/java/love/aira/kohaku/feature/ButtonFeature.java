package love.aira.kohaku.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import love.aira.kohaku.support.Mentions;

/**
 * 「命令进入 + 状态驱动按钮」骨架：最常用的一种功能形态（如分页卡片、菜单、列表），
 * 业务只需提供两个纯函数，管道由框架补齐。
 *
 * <pre>{@code
 * @Bean
 * BotFeature card(BotReplies replies) {
 *     return ButtonFeature.of("card")                            // 默认入口命令 = /card
 *             .state(CardFeature::state)                          // 文本 → 状态；null 表示"不是我的命令"
 *             .render(CardFeature::page)                          // 状态 → 消息（入口与按钮共用）
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
 *   <li>被动回复走 {@link BotReplies}（目标、msg_id/msg_seq、互动 event_id、超限告警全自动）。</li>
 * </ul>
 *
 * <p>状态即按钮 data 的 state 段（{@link ButtonData}），只承载渲染所需的键值（如页码），不要塞大对象；
 * 键盘怎么画、状态含义是什么属于业务，见示例 {@code CardKeyboards}。
 */
public final class ButtonFeature {

    /** 默认按钮动作：与 {@code CardKeyboards} 那类翻页键盘的 action 段一致。 */
    private static final List<String> DEFAULT_ACTIONS = List.of("next", "prev", "page");

    private ButtonFeature() {
    }

    public static Builder of(String featureId) {
        return new Builder(featureId);
    }

    public static final class Builder {

        private final String featureId;
        private final List<String> commands = new ArrayList<>();
        private final List<String> actions = new ArrayList<>(DEFAULT_ACTIONS);
        private Function<String, Map<String, String>> stateParser;
        private Function<Map<String, String>, SendMessageRequest> renderer;

        private Builder(String featureId) {
            Objects.requireNonNull(featureId, "featureId");
            ButtonData.requireSimple("featureId", featureId);   // 装配期校验，别等首次点击
            this.featureId = featureId;
        }

        /** 入口命令前缀（默认 {@code /<featureId>}）；文本等于前缀或以「前缀 + 空格」开头才算命中。 */
        public Builder commands(String... prefixes) {
            commands.clear();
            for (String prefix : prefixes) {
                if (prefix == null || prefix.isBlank()) {
                    throw new IllegalArgumentException("命令前缀不能为空");
                }
                commands.add(prefix.trim());
            }
            if (commands.isEmpty()) {
                throw new IllegalArgumentException("至少要有一个命令前缀");
            }
            return this;
        }

        /** 文本 → 按钮状态（{@link ButtonData} 的 state 键值）；返回 {@code null} 表示不是本功能的命令。 */
        public Builder state(Function<String, Map<String, String>> parser) {
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

        /** 状态 → 消息：入口与按钮回调**共用**这一个函数，保证两条路径渲染一致。 */
        public Builder render(Function<Map<String, String>, SendMessageRequest> renderer) {
            this.renderer = Objects.requireNonNull(renderer, "render");
            return this;
        }

        public BotFeature build(BotReplies replies) {
            Objects.requireNonNull(replies, "replies");
            Objects.requireNonNull(stateParser, "state（文本 → 状态）");
            Objects.requireNonNull(renderer, "render（状态 → 消息）");
            if (commands.isEmpty()) {
                commands.add("/" + featureId);
            }
            List<String> commandPrefixes = List.copyOf(commands);
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
                    SendMessageRequest request = render(context.state());
                    replies.send(context.interaction(), request);
                    return HandlerResult.CONSUMED;
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
            if (content == null || !matchesAny(content)) {
                return HandlerResult.IGNORED;   // 不是本功能的命令，交给后面的处理器
            }
            Map<String, String> state = stateParser.apply(content);
            if (state == null || state.isEmpty()) {
                return HandlerResult.IGNORED;
            }
            replies.send(event, render(state));
            return HandlerResult.CONSUMED;
        }

        private boolean matchesAny(String content) {
            String trimmed = content.trim();
            for (String prefix : commands) {
                if (trimmed.equals(prefix) || trimmed.startsWith(prefix + " ")) {
                    return true;
                }
            }
            return false;
        }

        private SendMessageRequest render(Map<String, String> state) {
            SendMessageRequest request = renderer.apply(Map.copyOf(state));
            if (request == null) {
                throw new IllegalStateException("render 返回了 null：状态 " + state + "（功能 " + featureId + "）");
            }
            return request;
        }
    }
}
