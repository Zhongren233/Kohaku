package love.aira.kohaku.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.model.Keyboard;
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
    /** 是否用回调按钮（需开放平台开通「互动事件」权限）；false 时用指令按钮，仅依赖消息链路。 */
    public static final boolean USE_INTERACTION_BUTTONS = false;
    public static final String ACTION_NEXT = FeatureKeyboards.ACTION_NEXT;
    public static final String ACTION_PREV = FeatureKeyboards.ACTION_PREV;
    public static final String ACTION_PAGE = FeatureKeyboards.ACTION_PAGE;
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

    /**
     * 渲染指定页（纯函数，便于测试）：markdown 文本 + 翻页键盘。
     *
     * <p>必须用 markdown（{@code msg_type=2}）承载键盘：实测纯文本消息上的自定义键盘会被客户端丢弃。
     *
     * <p>默认使用**指令按钮**（点击＝发一条 {@code /card …} 命令）：不依赖 {@code INTERACTION} 权限，
     * 整条链路只走消息事件。若机器人已开通互动事件权限，可把 {@link #USE_INTERACTION_BUTTONS} 打开，
     * 改为回调按钮（点击走 {@link love.aira.kohaku.feature.InteractionRouter} → 本功能的 ButtonHandler）。
     */
    public static SendMessageRequest page(int requestedPage) {
        Pagination pagination = Pagination.of(requestedPage, PAGE_SIZE, CARDS.size());
        StringBuilder markdown = new StringBuilder("# 卡片列表 ").append(pagination.label()).append("\n\n");
        for (Card card : pagination.slice(CARDS)) {
            markdown.append("- **").append(card.id()).append("** ").append(card.title())
                    .append(" —— ").append(card.summary()).append('\n');
        }
        markdown.append("\n> 点击下方按钮翻页");
        return SendMessageRequest.markdown(markdown.toString())
                .withKeyboard(USE_INTERACTION_BUTTONS
                        ? FeatureKeyboards.pagination(ID, pagination, Map.of())
                        : commandPagination(pagination));
    }

    /** 指令式翻页键盘：data 形如 {@code /card next 2}，点击后作为普通消息回到本功能的命令入口。 */
    private static Keyboard commandPagination(Pagination pagination) {
        List<Keyboard.Button> buttons = new ArrayList<>(3);
        if (pagination.hasPrevious()) {
            buttons.add(FeatureKeyboards.commandButton("prev", "上一页",
                    command(ACTION_PREV, pagination.page() - 1)));
        }
        buttons.add(FeatureKeyboards.commandButton("page", pagination.label(),
                command(ACTION_PAGE, pagination.page())));
        if (pagination.hasNext()) {
            buttons.add(FeatureKeyboards.commandButton("next", "下一页",
                    command(ACTION_NEXT, pagination.page() + 1)));
        }
        return Keyboard.of(Keyboard.Row.of(buttons.toArray(Keyboard.Button[]::new)));
    }

    private static String command(String action, int page) {
        return COMMAND + " " + action + " " + page;
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
                int page = event.payload() == null ? 0 : requestedPage(event.payload().content());
                if (page == 0) {
                    return HandlerResult.IGNORED;   // 不是本功能的命令，交给后面的处理器
                }
                messages.sendToUser(event.payload().author().userOpenid(),
                        page(page).replyingTo(event.payload().id(), 1));
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
                int page = event.payload() == null ? 0 : requestedPage(event.payload().content());
                if (page == 0) {
                    return HandlerResult.IGNORED;
                }
                messages.sendToGroup(event.payload().groupOpenid(),
                        page(page).replyingTo(event.payload().id(), 1));
                return HandlerResult.CONSUMED;
            }
        };
    }

    /** {@code /card} 或 {@code /card next 2} 等命令返回目标页码；不是本功能的命令返回 0。 */
    static int requestedPage(String content) {
        if (content == null) {
            return 0;
        }
        String trimmed = content.trim();
        if (!isCommand(trimmed)) {
            return 0;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            return 1;                                        // /card
        }
        if (parts.length == 2) {
            return parsePage(parts[1]);                       // /card 3
        }
        if (!ACTION_NEXT.equals(parts[1]) && !ACTION_PREV.equals(parts[1]) && !ACTION_PAGE.equals(parts[1])) {
            return 0;
        }
        return parsePage(parts[2]);                            // /card next 2（指令按钮点击后的形态）
    }

    private static int parsePage(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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
