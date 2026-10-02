package love.aira.kohaku.card;

import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.feature.ButtonCommands;
import love.aira.kohaku.feature.FeatureContext;
import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.support.CommandArgs;
import love.aira.kohaku.support.Pagination;
import org.springframework.context.annotation.Bean;
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

    /**
     * 渲染指定页（纯函数，便于测试）：markdown 文本 + 翻页键盘。
     *
     * <p>必须用 markdown（{@code msg_type=2}）承载键盘：实测纯文本消息上的自定义键盘会被客户端丢弃。
     *
     * <p>默认使用**指令按钮**（点击＝发一条 {@code /card …} 命令）：不依赖 {@code INTERACTION} 权限，
     * 整条链路只走消息事件。若机器人已开通互动事件权限，可把 {@link #USE_INTERACTION_BUTTONS} 打开，
     * 改为回调按钮（点击走 {@link love.aira.kohaku.interaction.InteractionRouter} → 本功能的 ButtonHandler）。
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
     * 装配为功能单元：命令入口 + 三个翻页按钮由 {@link ButtonCommands} 骨架生成，
     * 本类只提供「命令参数 → 状态」与「上下文 → 消息」两个纯函数。
     */
    @Bean
    BotFeature card(BotReplies replies) {
        return ButtonCommands.of(ID)
                .commands(COMMAND)          // 入口命令：/card；不匹配的文本一律 IGNORED
                .state(CardFeature::state)  // 命令参数 → 目标页码状态（null = 参数不成立）
                .render(CardFeature::page)  // 上下文 → 消息：入口与按钮共用，两条路径渲染一致
                .build(replies);
    }

    /** 命令参数 → 按钮状态（只放渲染需要的页码）；参数不成立时返回 {@code null}。 */
    static Map<String, String> state(CommandArgs args) {
        int page = switch (args.size()) {
            case 0 -> 1;                                                  // /card
            case 1 -> args.intAt(0, 0);                                   // /card 3
            default -> isAction(args.at(0)) ? args.intAt(1, 0) : 0;       // /card next 2（指令按钮点击后的形态）
        };
        return page == 0 ? null : Map.of(CardKeyboards.STATE_PAGE, Integer.toString(page));
    }

    /**
     * 上下文 → 消息（入口与按钮共用）。
     *
     * <p>需要按人/群差别渲染时从这里取：触发者是 {@code context.userOpenid()}（单聊与群聊同一个值，不必按场景分支），
     * 群是 {@code context.groupOpenid()}（仅群聊非空）；触发事件本身是 {@code context.event()}。
     */
    static SendMessageRequest page(FeatureContext context) {
        return page(context.intState(CardKeyboards.STATE_PAGE, 1));
    }

    /** 翻页键盘发出的动作词（{@code /card next 2} 这种形态）。 */
    private static boolean isAction(String action) {
        return CardKeyboards.ACTION_NEXT.equals(action) || CardKeyboards.ACTION_PREV.equals(action)
                || CardKeyboards.ACTION_PAGE.equals(action);
    }
}
