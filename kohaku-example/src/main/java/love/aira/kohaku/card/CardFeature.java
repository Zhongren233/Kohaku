package love.aira.kohaku.card;

import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.feature.ButtonContext;
import love.aira.kohaku.feature.ButtonHandler;
import love.aira.kohaku.feature.FeatureKeyboards;
import love.aira.kohaku.feature.InteractionReplies;
import love.aira.kohaku.feature.Pagination;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 示例功能：{@code /card} 卡片列表 + 按钮翻页。
 *
 * <p>入口是消息命令，翻页按钮是回调按钮：点击后的 {@code INTERACTION_CREATE} 由框架按按钮 data 里的
 * 功能命名空间（{@code card:next:p=2}）路由回本功能，因此翻页不会落到别的消息处理器。
 * 状态（当前页）直接放在按钮 data 里（方案 A：自描述），机器人侧不保存会话。
 */
@Component
public class CardFeature implements BotFeature {

    private static final Logger log = LoggerFactory.getLogger(CardFeature.class);

    /** 功能标识，同时是按钮 data 的命名空间。 */
    public static final String ID = "card";
    public static final String COMMAND = "/card";
    static final int PAGE_SIZE = 5;

    record Card(String id, String title, String summary) {
    }

    static final List<Card> CARDS = List.of(
            new Card("C-001", "晨间清单", "三件必做的小事"),
            new Card("C-002", "读书笔记", "本周摘要与划线"),
            new Card("C-003", "健身计划", "胸背腿三分化"),
            new Card("C-004", "旅行手账", "秋日周边两日游"),
            new Card("C-005", "菜谱收集", "十道快手菜"),
            new Card("C-006", "观影记录", "本月片单与评分"),
            new Card("C-007", "记账模板", "月度收支分类"),
            new Card("C-008", "学习路径", "分布式基础到实践"),
            new Card("C-009", "英语单词", "每日 20 词复习"),
            new Card("C-010", "灵感收集", "随手记的想法"),
            new Card("C-011", "项目看板", "进行中/待办/完成"),
            new Card("C-012", "复盘模板", "每周一次小复盘"));

    private final QqMessageApi messages;

    public CardFeature(QqMessageApi messages) {
        this.messages = messages;
    }

    @Override
    public String id() {
        return ID;
    }

    /** 渲染指定页（纯函数，便于测试）：文本 + 翻页键盘。 */
    public static SendMessageRequest page(int requestedPage) {
        Pagination pagination = Pagination.of(requestedPage, PAGE_SIZE, CARDS.size());
        StringBuilder content = new StringBuilder("卡片列表 ").append(pagination.label()).append('\n');
        for (Card card : pagination.slice(CARDS)) {
            content.append("· ").append(card.id()).append(' ').append(card.title())
                    .append(" —— ").append(card.summary()).append('\n');
        }
        content.append("（点击下方按钮翻页）");
        return SendMessageRequest.text(content.toString())
                .withKeyboard(FeatureKeyboards.pagination(ID, pagination, Map.of()));
    }

    @Override
    public List<BotEventHandler<?>> messageHandlers() {
        return List.of(c2cEntry(), groupEntry());
    }

    @Override
    public List<ButtonHandler> buttonHandlers() {
        return List.of(step(FeatureKeyboards.ACTION_NEXT, 1), step(FeatureKeyboards.ACTION_PREV, -1),
                step(FeatureKeyboards.ACTION_PAGE, 0));
    }

    /** 翻页按钮：读取按钮 data 里的页码并渲染目标页，用互动事件 id 做被动回复。 */
    private ButtonHandler step(String action, int delta) {
        return new ButtonHandler() {
            @Override
            public String action() {
                return action;
            }

            @Override
            public HandlerResult onButton(ButtonContext context) {
                int current = Pagination.of(context.intState(FeatureKeyboards.STATE_PAGE, 1), PAGE_SIZE, CARDS.size())
                        .page();
                int target = delta == 0 ? current : current + delta;
                log.info("card 翻页 {} → {} (scene={} user={})", current, target, context.scene(),
                        context.userOpenid() != null ? context.userOpenid() : context.groupOpenid());
                InteractionReplies.reply(messages, context, page(target));
                return HandlerResult.CONSUMED;
            }
        };
    }

    private BotEventHandler<C2cMessageCreateEvent> c2cEntry() {
        return new BotEventHandler<>() {
            @Override
            public Class<C2cMessageCreateEvent> eventType() {
                return C2cMessageCreateEvent.class;
            }

            @Override
            public HandlerResult handle(C2cMessageCreateEvent event) {
                if (event.payload() == null || !isCommand(event.payload().content())) {
                    return HandlerResult.IGNORED;   // 不是本功能的命令，交给后面的处理器
                }
                messages.sendToUser(event.payload().author().userOpenid(),
                        page(1).replyingTo(event.payload().id(), 1));
                return HandlerResult.CONSUMED;
            }
        };
    }

    private BotEventHandler<GroupAtMessageCreateEvent> groupEntry() {
        return new BotEventHandler<>() {
            @Override
            public Class<GroupAtMessageCreateEvent> eventType() {
                return GroupAtMessageCreateEvent.class;
            }

            @Override
            public HandlerResult handle(GroupAtMessageCreateEvent event) {
                if (event.payload() == null || !isCommand(event.payload().content())) {
                    return HandlerResult.IGNORED;
                }
                messages.sendToGroup(event.payload().groupOpenid(),
                        page(1).replyingTo(event.payload().id(), 1));
                return HandlerResult.CONSUMED;
            }
        };
    }

    /** {@code /card} 或 {@code /card 3} 都算本功能的命令。 */
    static boolean isCommand(String content) {
        if (content == null) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.equals(COMMAND) || trimmed.startsWith(COMMAND + " ");
    }
}
