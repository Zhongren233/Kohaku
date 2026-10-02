package love.aira.kohaku.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 重连退避：指数增长、抖动范围、封顶、异常入参。 */
class GatewayBackoffTest {

    /** 同一 attempt 取多次样本（抖动是随机的），便于断言区间。 */
    private static List<Long> samples(int attempt, long initial, long max) {
        return IntStream.range(0, 200).mapToObj(i -> GatewayBackoff.nextDelayMillis(attempt, initial, max)).toList();
    }

    @Test
    void growsExponentiallyWithinJitterBounds() {
        // ceiling = initial << attempt，返回值落在 [ceiling - ceiling/5, ceiling + ceiling/5)
        assertThat(samples(0, 1000, 60000)).allSatisfy(delay -> assertThat(delay).isBetween(800L, 1199L));
        assertThat(samples(1, 1000, 60000)).allSatisfy(delay -> assertThat(delay).isBetween(1600L, 2399L));
        assertThat(samples(3, 1000, 60000)).allSatisfy(delay -> assertThat(delay).isBetween(6400L, 9599L));
    }

    @Test
    void neverExceedsMaxDelay() {
        for (int attempt = 0; attempt < 30; attempt++) {
            assertThat(samples(attempt, 1000, 60000)).allSatisfy(delay -> assertThat(delay).isBetween(0L, 60000L));
        }
        // 抖动不会把结果推到上限之外：高次数时始终落在 [max - max/5, max]
        assertThat(samples(20, 1000, 60000)).allSatisfy(delay -> assertThat(delay).isBetween(48000L, 60000L));
    }

    @Test
    void jitterActuallyVaries() {
        assertThat(samples(3, 1000, 60000).stream().distinct().count()).isGreaterThan(1);
    }

    @Test
    void treatsInvalidInputsConservatively() {
        // max 小于 initial：按 initial 处理，不产生比 initial 更大的延迟
        assertThat(samples(5, 5000, 1000)).allSatisfy(delay -> assertThat(delay).isBetween(0L, 5000L));
        // initial <= 0：至少 1ms 基准，不出现负数
        assertThat(samples(0, 0, 60000)).allSatisfy(delay -> assertThat(delay).isBetween(0L, 1L));
        // 负数 attempt 等同 0
        assertThat(samples(-3, 1000, 60000)).allSatisfy(delay -> assertThat(delay).isBetween(800L, 1199L));
    }

    @Test
    void resumesOnlyWithBothSessionAndSeq() {
        assertThat(GatewayBackoff.resumable("SESSION", 42L)).isTrue();
        assertThat(GatewayBackoff.resumable(null, 42L)).isFalse();
        assertThat(GatewayBackoff.resumable("SESSION", null)).isFalse();
        assertThat(GatewayBackoff.resumable(null, null)).isFalse();
    }
}
