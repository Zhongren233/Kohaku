package love.aira.kohaku.gateway;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 重连退避策略：指数增长 + 抖动，封顶在配置的上限。
 *
 * <p>提出来是为了可测：延迟只由「重连次数 + 两个配置值」决定（抖动除外），不依赖连接状态。
 */
final class GatewayBackoff {

    /** 左移次数上限，避免 {@code base << attempt} 溢出（2^16 × 1s 已远超任何合理上限）。 */
    private static final long MAX_SHIFT = 16;

    private GatewayBackoff() {
    }

    /**
     * 第 {@code attempt} 次重连的延迟（毫秒）。
     *
     * <p>上限为 {@code ceiling = min(maxMillis, initialMillis << attempt)}，实际返回
     * {@code [ceiling - ceiling/5, ceiling + ceiling/5)} 内的随机值，再封顶到 {@code maxMillis}
     * —— 抖动避免多分片同时重连。{@code maxMillis < initialMillis} 时按 {@code initialMillis} 处理。
     *
     * @param attempt       重连次数，从 0 开始（负数按 0 处理）
     * @param initialMillis 起始延迟，最小按 1ms 处理
     * @param maxMillis     延迟上限
     */
    static long nextDelayMillis(int attempt, long initialMillis, long maxMillis) {
        long base = Math.max(1, initialMillis);
        long max = Math.max(base, maxMillis);
        long ceiling = Math.min(max, base << Math.min(Math.max(attempt, 0), MAX_SHIFT));
        long spread = Math.max(1, ceiling / 5);
        return Math.min(max, ceiling - spread + ThreadLocalRandom.current().nextLong(2 * spread));
    }

    /** 是否具备 Resume 条件：既要有 session_id，也要有已收到的 seq。 */
    static boolean resumable(String sessionId, Long lastSeq) {
        return sessionId != null && lastSeq != null;
    }
}
