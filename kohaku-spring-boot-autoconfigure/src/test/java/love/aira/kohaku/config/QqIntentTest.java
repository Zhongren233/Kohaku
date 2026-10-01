package love.aira.kohaku.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class QqIntentTest {

    @Test
    void everyIntentOccupiesADistinctBit() {
        List<Integer> bits = Arrays.stream(QqIntent.values()).map(QqIntent::bit).toList();
        assertThat(bits).doesNotHaveDuplicates();
    }

    @Test
    void bitsMatchPlatformDefinition() {
        assertThat(QqIntent.GUILDS.bit()).isEqualTo(1 << 0);
        assertThat(QqIntent.GUILD_MEMBERS.bit()).isEqualTo(1 << 1);
        assertThat(QqIntent.GUILD_MESSAGES.bit()).isEqualTo(1 << 9);
        assertThat(QqIntent.GUILD_MESSAGE_REACTIONS.bit()).isEqualTo(1 << 10);
        assertThat(QqIntent.DIRECT_MESSAGE.bit()).isEqualTo(1 << 12);
        assertThat(QqIntent.GROUP_AND_C2C_EVENT.bit()).isEqualTo(1 << 25);
        assertThat(QqIntent.INTERACTION.bit()).isEqualTo(1 << 26);
        assertThat(QqIntent.MESSAGE_AUDIT.bit()).isEqualTo(1 << 27);
        assertThat(QqIntent.FORUMS_EVENT.bit()).isEqualTo(1 << 28);
        assertThat(QqIntent.AUDIO_ACTION.bit()).isEqualTo(1 << 29);
        assertThat(QqIntent.PUBLIC_GUILD_MESSAGES.bit()).isEqualTo(1 << 30);
    }

    @Test
    void maskCombinesSelectedIntents() {
        assertThat(QqIntent.mask(List.of())).isZero();
        assertThat(QqIntent.mask(List.of(QqIntent.PUBLIC_GUILD_MESSAGES))).isEqualTo(1 << 30);
        assertThat(QqIntent.mask(List.of(QqIntent.GUILDS, QqIntent.GROUP_AND_C2C_EVENT,
                QqIntent.PUBLIC_GUILD_MESSAGES)))
                .isEqualTo((1 << 0) | (1 << 25) | (1 << 30));
        // 全选后的掩码既不溢出 int，也等于各位之和（即位之间不重叠）
        int sum = Arrays.stream(QqIntent.values()).mapToInt(QqIntent::bit).sum();
        assertThat(QqIntent.mask(List.of(QqIntent.values()))).isEqualTo(sum).isPositive();
    }
}
