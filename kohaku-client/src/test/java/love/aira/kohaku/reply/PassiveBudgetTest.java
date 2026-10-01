package love.aira.kohaku.reply;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import love.aira.kohaku.reply.PassiveBudget.Scene;
import love.aira.kohaku.reply.PassiveBudget.Usage;
import org.junit.jupiter.api.Test;

/** 被动回复的窗口/次数记账：告警文案与触发时机（假时钟，不 sleep）。 */
class PassiveBudgetTest {

    private final TestClock clock = new TestClock(Instant.parse("2026-10-02T10:00:00Z"));
    private final PassiveBudget budget = new PassiveBudget(clock);

    @Test
    void staysSilentWithinPlatformLimits() {
        assertThat(budget.record("MSG_1", Scene.C2C).warning()).isNull();
        Usage second = budget.record("MSG_1", Scene.C2C);
        assertThat(second.sequence()).isEqualTo(2);
        assertThat(second.warning()).isNull();
    }

    @Test
    void warnsOnTheLastAllowedReplyAndAfterwards() {
        for (int i = 1; i <= 3; i++) {
            budget.record("MSG_1", Scene.C2C);
        }

        Usage last = budget.record("MSG_1", Scene.C2C);          // 第 4 次：刚好用满
        assertThat(last.warning()).contains("单聊").contains("已用满").contains("4 次");

        Usage overflow = budget.record("MSG_1", Scene.C2C);      // 第 5 次：平台会拒
        assertThat(overflow.warning()).contains("超过平台上限").contains("5 次");
    }

    @Test
    void groupLimitIsFive() {
        for (int i = 1; i <= 4; i++) {
            assertThat(budget.record("MSG_G", Scene.GROUP).warning()).isNull();
        }
        assertThat(budget.record("MSG_G", Scene.GROUP).warning()).contains("群聊").contains("已用满").contains("5 次");
    }

    @Test
    void warnsWhenThePassiveWindowHasExpired() {
        budget.record("MSG_1", Scene.C2C);

        clock.advance(Duration.ofMinutes(61));

        assertThat(budget.record("MSG_1", Scene.C2C).warning())
                .contains("窗口").contains("已过").contains("60 分钟");
    }

    @Test
    void countsSequencesPerMessage() {
        assertThat(budget.record("MSG_1", Scene.C2C).sequence()).isEqualTo(1);
        assertThat(budget.record("MSG_2", Scene.C2C).sequence()).isEqualTo(1);
        assertThat(budget.record("MSG_1", Scene.C2C).sequence()).isEqualTo(2);
    }

    @Test
    void channelsHaveNoReplyCountLimit() {
        for (int i = 1; i <= 12; i++) {
            assertThat(budget.record("MSG_CH", Scene.GUILD).warning()).isNull();
        }
    }

    /** 可手动推进的时钟（窗口告警不靠 sleep 验证）。 */
    private static final class TestClock extends Clock {

        private Instant now;

        private TestClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
