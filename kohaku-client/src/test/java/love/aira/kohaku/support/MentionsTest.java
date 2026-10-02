package love.aira.kohaku.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import love.aira.kohaku.gateway.event.model.MessageAuthor;
import org.junit.jupiter.api.Test;

/** 行首 @ 提及剥离：只剥行首、只剥机器人自己的提及（真机报文格式见 {@link Mentions} 类注释）。 */
class MentionsTest {

    private static final String BOT_ID = "1D04787ECFDF37BFC3984D425AA56383";

    @Test
    void stripsLeadingBotMention() {
        assertThat(Mentions.stripLeading("<@" + BOT_ID + "> /card", List.of(bot())))
                .isEqualTo("/card");
    }

    @Test
    void stripsNicknameStyleMentionAndSurroundingWhitespace() {
        assertThat(Mentions.stripLeading("  <@!" + BOT_ID + ">\n\t/card 2", List.of(bot())))
                .isEqualTo("/card 2");
    }

    @Test
    void stripsRepeatedLeadingBotMentions() {
        assertThat(Mentions.stripLeading("<@" + BOT_ID + "> <@" + BOT_ID + "> /card", List.of(bot())))
                .isEqualTo("/card");
    }

    /** 别人被 @ 时不能剥：{@code @Alice /card} 不是给机器人的命令。 */
    @Test
    void keepsMentionOfAnotherMember() {
        String content = "<@ALICE_OPENID> /card";
        assertThat(Mentions.stripLeading(content, List.of(member("ALICE_OPENID"))))
                .isEqualTo(content);
    }

    @Test
    void onlyStripsLeadingTokenNotInnerMentions() {
        String content = "你好 <@" + BOT_ID + "> /card";
        assertThat(Mentions.stripLeading(content, List.of(bot()))).isEqualTo(content);
    }

    /** 群 @ 事件不下发 mentions，此时按平台语法尽力剥离。 */
    @Test
    void stripsWhenMentionsAbsent() {
        assertThat(Mentions.stripLeading("<@" + BOT_ID + "> /card", List.of())).isEqualTo("/card");
        assertThat(Mentions.stripLeading("<@" + BOT_ID + "> /card", null)).isEqualTo("/card");
    }

    @Test
    void returnsOriginalWhenNothingToStrip() {
        assertThat(Mentions.stripLeading("/card", List.of(bot()))).isEqualTo("/card");
        assertThat(Mentions.stripLeading("  /card", List.of(bot()))).isEqualTo("  /card");   // 非提及内容原样返回
        assertThat(Mentions.stripLeading("", List.of(bot()))).isEmpty();
        assertThat(Mentions.stripLeading(null, List.of(bot()))).isNull();
    }

    @Test
    void malformedTokenIsLeftAlone() {
        String content = "<@" + BOT_ID + " /card";   // 缺少 '>'
        assertThat(Mentions.stripLeading(content, List.of(bot()))).isEqualTo(content);
    }

    private static MessageAuthor bot() {
        return new MessageAuthor(BOT_ID, "zz", true, null, null, null, null, BOT_ID, "member");
    }

    private static MessageAuthor member(String id) {
        return new MessageAuthor(id, "Alice", false, null, null, null, null, id, "member");
    }
}
