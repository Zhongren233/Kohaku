package love.aira.kohaku.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link CommandArgs}：命令分词、参数取值与子命令下钻。 */
class CommandArgsTest {

    @Test
    void parsesCommandWordAndArguments() {
        CommandArgs args = CommandArgs.parse("  /todo add 买菜 和 牛奶 ");

        assertThat(args.command()).isEqualTo("/todo");
        assertThat(args.args()).containsExactly("add", "买菜", "和", "牛奶");
        assertThat(args.size()).isEqualTo(4);
        assertThat(args.isEmpty()).isFalse();
        assertThat(args.at(0)).isEqualTo("add");
        assertThat(args.at(99)).isNull();
    }

    @Test
    void anyWhitespaceSeparatesTokens() {
        CommandArgs args = CommandArgs.parse("/card\tnext \n 2");

        assertThat(args.command()).isEqualTo("/card");
        assertThat(args.args()).containsExactly("next", "2");
    }

    @Test
    void blankOrNullInputIsNotACommand() {
        assertThat(CommandArgs.parse(null)).isNull();
        assertThat(CommandArgs.parse("")).isNull();
        assertThat(CommandArgs.parse("   \t ")).isNull();
    }

    @Test
    void intAtFallsBackOnOutOfRangeOrNonNumeric() {
        CommandArgs args = CommandArgs.parse("/todo 3");

        assertThat(args.intAt(0, -1)).isEqualTo(3);
        assertThat(args.intAt(1, -1)).isEqualTo(-1);                       // 越界
        assertThat(CommandArgs.parse("/todo abc").intAt(0, -1)).isEqualTo(-1);   // 非数字
    }

    @Test
    void tailPromotesFirstArgumentToCommand() {
        CommandArgs root = CommandArgs.parse("/todo add 买菜 和 牛奶");

        CommandArgs sub = root.tail();

        assertThat(sub.command()).isEqualTo("add");
        assertThat(sub.args()).containsExactly("买菜", "和", "牛奶");
        assertThat(root.command()).isEqualTo("/todo");                     // 原对象不变
        assertThat(root.args()).containsExactly("add", "买菜", "和", "牛奶");
    }

    @Test
    void tailIsNullWithoutArguments() {
        assertThat(CommandArgs.parse("/todo").tail()).isNull();
    }

    @Test
    void tailDescendsThroughNestedSubcommands() {
        CommandArgs add = CommandArgs.parse("/admin user add alice").tail().tail();

        assertThat(add.command()).isEqualTo("add");
        assertThat(add.args()).containsExactly("alice");
        assertThat(add.tail().command()).isEqualTo("alice");
        assertThat(add.tail().args()).isEmpty();
    }
}
