package love.aira.kohaku.todo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import love.aira.kohaku.feature.BotFeature;
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
 * 示例功能：{@code /todo} 命令 + 子命令 {@code add}/{@code list}/{@code done}。
 *
 * <p>演示「一个命令词下挂多个子命令、各自行为不同」的写法：子命令就是 {@link CommandArgs} 的
 * **第一个参数**，用 {@code tail()} 把它提升为新的命令词后递归分派，因此 {@code /todo add 买菜}
 * 与二级、三级子命令是同一段逻辑。子命令各自渲染、不共用一条「状态 → 消息」管线，所以直接用
 * {@link BotFeature} 装配；若子命令共享同一次渲染（如翻页），用
 * {@link love.aira.kohaku.feature.ButtonFeature} 更轻（见 {@code CardFeature}）。
 *
 * <p>待办存在内存里，仅作示例；真实业务应落到存储。
 */
@Component
public class TodoFeature {

    /** 功能标识，同时是按钮 data 的命名空间。 */
    public static final String ID = "todo";
    /** 入口命令词。 */
    public static final String COMMAND = "/todo";

    private static final String HELP = "用法: " + COMMAND + " add <内容> | list | done <编号>";

    private final List<String> items = new CopyOnWriteArrayList<>();

    @Bean
    BotFeature todo(BotReplies replies) {
        return BotFeature.of(ID)
                .message(C2cMessageCreateEvent.class, event -> route(event, c2cText(event), replies))
                .message(GroupAtMessageCreateEvent.class, event -> route(event, groupText(event.payload()), replies))
                .message(GroupMessageCreateEvent.class, event -> route(event, groupText(event.payload()), replies))
                .build();
    }

    /** 命令词匹配 + 子命令分派；不属于本功能的文本一律 {@code IGNORED}，交回处理链。 */
    private HandlerResult route(BotEvent event, String content, BotReplies replies) {
        CommandArgs root = CommandArgs.parse(content);
        if (root == null || !COMMAND.equals(root.command())) {
            return HandlerResult.IGNORED;
        }
        CommandArgs sub = tail(root);
        if (sub == null) {
            replies.text(event, HELP);
            return HandlerResult.CONSUMED;
        }
        return switch (sub.command()) {
            case "add" -> add(sub, event, replies);
            case "list" -> list(event, replies);
            case "done" -> done(sub, event, replies);
            default -> {
                replies.text(event, "未知子命令: " + sub.command() + "\n" + HELP);
                yield HandlerResult.CONSUMED;
            }
        };
    }

    /**
     * 去掉命令词，把第一个参数提升为新的命令词；没有参数时返回 {@code null}。
     *
     * <p>{@code /todo add 买菜} → {@code command="add", args=["买菜"]}，再 {@code tail()} 即可继续下钻。
     */
    private static CommandArgs tail(CommandArgs args) {
        if (args.isEmpty()) {
            return null;
        }
        return new CommandArgs(args.at(0), args.args().subList(1, args.args().size()));
    }

    /** {@code /todo add 买菜 和 牛奶} → 新增一条「买菜 和 牛奶」。 */
    private HandlerResult add(CommandArgs args, BotEvent event, BotReplies replies) {
        if (args.isEmpty()) {
            replies.text(event, "用法: " + COMMAND + " add <内容>");
            return HandlerResult.CONSUMED;
        }
        String item = String.join(" ", args.args());
        items.add(item);
        replies.text(event, "已添加 #" + items.size() + " " + item);
        return HandlerResult.CONSUMED;
    }

    private HandlerResult list(BotEvent event, BotReplies replies) {
        if (items.isEmpty()) {
            replies.text(event, "待办为空，用 " + COMMAND + " add <内容> 添加");
            return HandlerResult.CONSUMED;
        }
        StringBuilder text = new StringBuilder("待办:");
        for (int i = 0; i < items.size(); i++) {
            text.append('\n').append(i + 1).append(". ").append(items.get(i));
        }
        replies.text(event, text.toString());
        return HandlerResult.CONSUMED;
    }

    /** {@code /todo done 2} → 完成第 2 条。 */
    private HandlerResult done(CommandArgs args, BotEvent event, BotReplies replies) {
        int index = args.intAt(0, -1);
        if (index < 1 || index > items.size()) {
            replies.text(event, "用法: " + COMMAND + " done <1-" + items.size() + ">");
            return HandlerResult.CONSUMED;
        }
        String item = items.remove(index - 1);
        replies.text(event, "已完成 #" + index + " " + item);
        return HandlerResult.CONSUMED;
    }

    private static String c2cText(C2cMessageCreateEvent event) {
        return event.payload() == null ? null : event.payload().content();
    }

    /** 群全量消息的 content 保留行首 {@code <@机器人id>}，匹配命令词前先剥掉（群 @ 消息平台已剥好）。 */
    private static String groupText(GroupMessage message) {
        return message == null ? null : Mentions.stripLeading(message.content(), message.mentions());
    }
}
