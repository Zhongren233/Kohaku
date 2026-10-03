package love.aira.kohaku.todo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.feature.FeatureContext;
import love.aira.kohaku.interaction.FeatureKeyboards;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.handler.HandlerResult;
import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.support.CommandArgs;
import love.aira.kohaku.support.Mentions;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 示例功能：{@code /todo} 命令 + 子命令 {@code add}/{@code list}/{@code done}，列表带「完成」按钮。
 *
 * <p>两种入口最终汇到同一段逻辑：
 * <ul>
 *   <li><b>文本子命令</b>：靠 {@link CommandArgs#tail()} 把子命令提升为命令词后分派
 *       （{@code /todo done 3}），二级 / 三级子命令是同一段递归；</li>
 *   <li><b>按钮回调</b>：列表里每条待办一个「完成」按钮，data 以功能命名空间编码
 *       （{@code todo:done:id=3}），由 {@code InteractionRouter} 投递回本功能的
 *       {@code done} 动作，与文本路径共用 {@link #done}。</li>
 * </ul>
 *
 * <p>待办项带一个只增不复用的 {@code id}，按钮携带它而非行号：列表变化后旧消息上的按钮
 * 仍然指向同一条待办，重复点击不会误删。<b>回调按钮</b>需要开通「互动事件」权限；未开通时把
 * {@link #USE_INTERACTION_BUTTONS} 置为 false，改用**指令按钮**（点击即发 {@code /todo done N}），
 * 整条链路只依赖消息事件。
 *
 * <p><b>待办按触发者隔离</b>：分区键是 {@link FeatureContext#userOpenid()}（单聊取 {@code user_openid}、
 * 群聊取 {@code group_member_openid}，同一个人是同一个值），所以同一个人在群里和单聊里看到的是同一份列表，
 * 不同人互不可见；按钮即使被转发给他人，也只会落到点击者自己的列表上。
 *
 * <p>待办存在内存里，仅作示例；真实业务应落到存储，并把分区键换成自己的用户标识。
 */
@Component
public class TodoFeature {

    /** 功能标识，同时是按钮 data 的命名空间。 */
    public static final String ID = "todo";
    /** 入口命令词。 */
    public static final String COMMAND = "/todo";
    /** 是否用回调按钮（需开放平台开通「互动事件」权限）；false 时用指令按钮，仅依赖消息链路。 */
    public static final boolean USE_INTERACTION_BUTTONS = true;

    static final String ACTION_DONE = "done";
    /** 按钮 data 里表示待办 id 的状态键。 */
    static final String STATE_ID = "id";

    private static final String HELP = "用法: " + COMMAND + " add <内容> | list | done <编号>";

    /** 待办项：{@code id} 新增时递增、删除后不复用，所以按钮携带它比携带行号稳定。 */
    private record Item(int id, String text) {
    }

    /** 触发者 OpenID → 该人的待办列表。 */
    private final Map<String, List<Item>> itemsByUser = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    @Bean
    BotFeature todo(BotReplies replies) {
        return BotFeature.of(ID)
                .message(C2cMessageCreateEvent.class, event -> route(event, c2cText(event), replies))
                .message(GroupAtMessageCreateEvent.class, event -> route(event, groupText(event.payload()), replies))
                .message(GroupMessageCreateEvent.class, event -> route(event, groupText(event.payload()), replies))
                .button(ACTION_DONE, context -> done(context.userOpenid(), context.intState(STATE_ID, -1),
                        context.interaction(), replies))
                .build();
    }

    /** 命令词匹配 + 子命令分派；不属于本功能的文本一律 {@code IGNORED}，交回处理链。 */
    private HandlerResult route(BotEvent event, String content, BotReplies replies) {
        CommandArgs root = CommandArgs.parse(content);
        if (root == null || !COMMAND.equals(root.command())) {
            return HandlerResult.IGNORED;
        }
        FeatureContext context = FeatureContext.ofMessage(event, Map.of());
        if (context.userOpenid() == null) {
            return HandlerResult.IGNORED;   // 平台没下发触发者标识（异常报文）：交给后面的处理器
        }
        String owner = context.userOpenid();
        List<Item> items = itemsOf(owner);
        CommandArgs sub = root.tail();
        if (sub == null) {
            return HandlerResult.consumed(() -> replies.text(event, HELP));
        }
        return switch (sub.command()) {
            case "add" -> add(sub, items, event, replies);
            case "list" -> render(items, event, "", replies);
            case "done" -> done(owner, sub.intAt(0, -1), event, replies);
            default -> HandlerResult.consumed(
                    () -> replies.text(event, "未知子命令: " + sub.command() + "\n" + HELP));
        };
    }

    /** 取某人的待办列表（首次访问即建）。 */
    private List<Item> itemsOf(String owner) {
        return itemsByUser.computeIfAbsent(owner, key -> new CopyOnWriteArrayList<>());
    }

    /** {@code /todo add 买菜 和 牛奶} → 新增一条并回显带按钮的列表。 */
    private HandlerResult add(CommandArgs args, List<Item> items, BotEvent event, BotReplies replies) {
        if (args.isEmpty()) {
            return HandlerResult.consumed(() -> replies.text(event, "用法: " + COMMAND + " add <内容>"));
        }
        Item item = new Item(nextId.getAndIncrement(), String.join(" ", args.args()));
        items.add(item);
        return render(items, event, "已添加 #" + item.id() + " " + item.text() + "\n\n", replies);
    }

    /**
     * 完成指定 id 的待办——文本 {@code /todo done 3} 与按钮回调共用这一条路径。
     *
     * <p>只在**触发者自己的**列表里查（按钮可能被转发给他人），因此 id 撞车也不会误删别人的待办。
     */
    private HandlerResult done(String owner, int id, BotEvent event, BotReplies replies) {
        if (owner == null) {
            return HandlerResult.IGNORED;   // 与入口一致：拿不到触发者就交给后面的处理器
        }
        List<Item> items = itemsOf(owner);
        if (id < 1) {
            return HandlerResult.consumed(() -> replies.text(event, "用法: " + COMMAND + " done <编号>"));
        }
        Item removed = items.stream().filter(item -> item.id() == id).findFirst().orElse(null);
        if (removed == null) {
            return HandlerResult.consumed(() -> replies.text(event, "没有 #" + id + " 这条待办\n" + HELP));
        }
        items.remove(removed);
        return render(items, event, "已完成 #" + id + " " + removed.text() + "\n\n", replies);
    }

    /** 渲染当前列表（markdown + 每条一个「完成」按钮）；列表入口、增删后共用同一路径。 */
    private HandlerResult render(List<Item> items, BotEvent event, String headline, BotReplies replies) {
        if (items.isEmpty()) {
            return HandlerResult.consumed(
                    () -> replies.text(event, headline + "待办为空，用 " + COMMAND + " add <内容> 添加"));
        }
        StringBuilder markdown = new StringBuilder(headline).append("**待办**");
        for (Item item : items) {
            markdown.append("\n- **#").append(item.id()).append(' ').append(item.text()).append("**");
        }
        // 消息文本与键盘在派发线程上一次性快照，只有网络发送是异步的（避免副作用途中列表被改动）
        String text = markdown.toString();
        Keyboard buttons = keyboard(items);
        return HandlerResult.consumed(() -> replies.markdown(event, text, buttons));
    }

    /**
     * 每条待办一个「完成」按钮。回调按钮把 {@code todo:done:id=N} 回调给本功能；指令按钮则点击即发
     * {@code /todo done N} —— 两条路径最终都进 {@link #done}。
     */
    private Keyboard keyboard(List<Item> items) {
        List<Keyboard.Row> rows = items.stream()
                .map(item -> Keyboard.Row.of(USE_INTERACTION_BUTTONS
                        ? FeatureKeyboards.button("done-" + item.id(), ID, ACTION_DONE, "完成 #" + item.id(),
                                Map.of(STATE_ID, Integer.toString(item.id())))
                        : FeatureKeyboards.commandButton("done-" + item.id(), "完成 #" + item.id(),
                                COMMAND + " done " + item.id())))
                .toList();
        return Keyboard.of(rows);
    }

    private static String c2cText(C2cMessageCreateEvent event) {
        return event.payload() == null ? null : event.payload().content();
    }

    /** 群全量消息的 content 保留行首 {@code <@机器人id>}，匹配命令词前先剥掉（群 @ 消息平台已剥好）。 */
    private static String groupText(GroupMessage message) {
        return message == null ? null : Mentions.stripLeading(message.content(), message.mentions());
    }
}
