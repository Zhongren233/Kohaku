package love.aira.kohaku.support;

import java.util.List;

/**
 * 命令文本的解析结果：命令词 + 空白分隔的参数。
 *
 * <p>把「命令匹配」与「参数解析」统一成**一次分词**。此前框架用 {@code prefix + " "}（字面空格）判定命中、
 * 业务侧却用 {@code \s+} 切参数，两套规则会分叉（如 {@code /card\t3} 匹配失败）；现在两者都基于本类的分词结果。
 *
 * <p>{@link #parse(String)} 对 {@code null}/全空白输入返回 {@code null}；命令词原样保留（含前导 {@code /}），
 * 参数按出现顺序保留。
 *
 * <pre>{@code
 * CommandArgs.parse("  /card next 2 ")   // command="/card", args=["next","2"]
 * CommandArgs.parse("/card")             // command="/card", args=[]
 * CommandArgs.parse("   ")               // null
 * }</pre>
 */
public record CommandArgs(String command, List<String> args) {

    public CommandArgs {
        args = List.copyOf(args);
    }

    /**
     * 按空白分词解析命令文本。
     *
     * @return 命令词与参数；输入为 {@code null} 或全空白时返回 {@code null}
     */
    public static CommandArgs parse(String content) {
        if (content == null) {
            return null;
        }
        String[] parts = content.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        return new CommandArgs(parts[0], List.of(parts).subList(1, parts.length));
    }

    /** 参数个数（不含命令词）。 */
    public int size() {
        return args.size();
    }

    public boolean isEmpty() {
        return args.isEmpty();
    }

    /** 第 {@code index} 个参数；越界返回 {@code null}。 */
    public String at(int index) {
        return index >= 0 && index < args.size() ? args.get(index) : null;
    }

    /** 第 {@code index} 个参数解析为 {@code int}；越界或非数字返回 {@code defaultValue}。 */
    public int intAt(int index, int defaultValue) {
        String value = at(index);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 去掉当前命令词，把第一个参数提升为新的命令词（子命令分派，可逐级递归）。
     *
     * <p>{@code parse("/todo add 买菜").tail()} → {@code command="add", args=["买菜"]}；
     * 对 {@code /admin user add alice} 连续 {@code tail()} 两次即得到 {@code add alice}。
     *
     * @return 新的 {@code CommandArgs}；没有参数时返回 {@code null}
     */
    public CommandArgs tail() {
        if (args.isEmpty()) {
            return null;
        }
        return new CommandArgs(args.getFirst(), args.subList(1, args.size()));
    }
}
