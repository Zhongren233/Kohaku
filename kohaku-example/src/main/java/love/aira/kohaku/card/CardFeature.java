package love.aira.kohaku.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.gateway.event.BotEvent;
import org.springframework.context.annotation.Bean;
import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.feature.ButtonContext;
import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.support.Pagination;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
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
public class CardFeature {

    private static final Logger log = LoggerFactory.getLogger(CardFeature.class);

    /** 功能标识，同时是按钮 data 的命名空间。 */
    public static final String ID = "card";
    public static final String COMMAND = "/card";
    /** 是否用回调按钮（需开放平台开通「互动事件」权限）；false 时用指令按钮，仅依赖消息链路。 */
    public static final boolean USE_INTERACTION_BUTTONS = true;
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

    private final BotReplies replies;

    public CardFeature(BotReplies replies) {
        this.replies = replies;
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
                        ? CardKeyboards.callbackPagination(ID, pagination, Map.of())
                        : CardKeyboards.commandPagination(ID, pagination));
    }

    /**
     * 装配为功能单元：一个命令入口（单聊/群聊各注册一次）+ 三个翻页按钮，全是 lambda。
     *
     * <p>函数式写法下消息入口的事件类型由参数给出（不用再写 {@code eventType()}），
     * 功能 id 与按钮 action 在**装配期**校验（写错是启动失败，而不是首次点击才失败）。
     */
    @Bean
    BotFeature card() {   // Bean 名取 card：避免与 @Component 的默认名 cardFeature 撞车
        return BotFeature.of(ID)
                .message(C2cMessageCreateEvent.class, event -> entry(event, content(event)))
                .message(GroupAtMessageCreateEvent.class, event -> entry(event, content(event)))
                .button(CardKeyboards.ACTION_NEXT, this::step)
                .button(CardKeyboards.ACTION_PREV, this::step)
                .button(CardKeyboards.ACTION_PAGE, this::step)
                .build();
    }

    /** 命令入口（单聊/群聊共用）：不是本功能的命令一律 {@code IGNORED}，交给后面的处理器。 */
    private HandlerResult entry(BotEvent event, String content) {
        int target = requestedPage(content);
        if (target == 0) {
            return HandlerResult.IGNORED;
        }
        replies.send(event, page(target));
        return HandlerResult.CONSUMED;
    }

    /**
     * 翻页按钮：按钮 data 里携带的是**目标页码**（`CardKeyboards` 已算好，指令按钮 `/card next 2` 同理），
     * 这里直接渲染目标页，不做加减 —— 重复点击/重放也是幂等的。
     */
    private HandlerResult step(ButtonContext context) {
        int target = context.intState(CardKeyboards.STATE_PAGE, 1);
        log.info("card 翻页 action={} target={} scene={} user={} group={}", context.action(), target, context.scene(),
                context.userOpenid(), context.groupOpenid());
        replies.send(context.interaction(), page(target));
        return HandlerResult.CONSUMED;
    }

    private static String content(BotEvent event) {
        return switch (event) {
            case C2cMessageCreateEvent c2c -> c2c.payload() == null ? null : c2c.payload().content();
            case GroupAtMessageCreateEvent group -> group.payload() == null ? null : group.payload().content();
            default -> null;
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
        if (!CardKeyboards.ACTION_NEXT.equals(parts[1]) && !CardKeyboards.ACTION_PREV.equals(parts[1])
                && !CardKeyboards.ACTION_PAGE.equals(parts[1])) {
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
