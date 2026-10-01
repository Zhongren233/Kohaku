package love.aira.kohaku.reply;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 被动回复的窗口与次数记账：按 {@code msg_id} 记「首次回复时间 + 已回复次数」，供 {@link BotReplies} 在
 * 撞上平台限制**之前**告警（平台会直接拒绝，报错信息不会告诉你还差几次）。
 *
 * <p>官方限制：单聊被动回复 60 分钟内有效、同一消息最多 4 次；群聊 5 分钟内有效、最多 5 次；
 * 频道消息 5 分钟内有效（未规定次数）。
 */
final class PassiveBudget {

    /** 平台限制（名称用于告警文案）。 */
    enum Scene {

        C2C("单聊", 4, Duration.ofMinutes(60)),
        GROUP("群聊", 5, Duration.ofMinutes(5)),
        GUILD("频道", Integer.MAX_VALUE, Duration.ofMinutes(5));

        private final String label;
        private final int maxReplies;
        private final Duration window;

        Scene(String label, int maxReplies, Duration window) {
            this.label = label;
            this.maxReplies = maxReplies;
            this.window = window;
        }
    }

    /** 记账结果：本次是该消息的第几次（即 msg_seq），以及需要提前告警时的说明（无需告警为 {@code null}）。 */
    record Usage(int sequence, String warning) {
    }

    /** 只保留最近用过的若干 msg_id，避免无界增长；被挤掉的只是少了告警，不影响发送。 */
    private static final int MAX_TRACKED = 4096;

    private record Tracked(int count, Instant first) {
    }

    private final Map<String, Tracked> entries = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Tracked> eldest) {
                    return size() > MAX_TRACKED;
                }
            });

    private final Clock clock;

    PassiveBudget(Clock clock) {
        this.clock = clock;
    }

    /** 记一次回复，返回本次序号与预警文本。 */
    Usage record(String messageId, Scene scene) {
        Instant now = clock.instant();
        Tracked previous = entries.get(messageId);
        Instant first = previous == null ? now : previous.first();
        int count = previous == null ? 1 : previous.count() + 1;
        entries.put(messageId, new Tracked(count, first));
        return new Usage(count, warning(scene, count, first, now));
    }

    private static String warning(Scene scene, int count, Instant first, Instant now) {
        long minutes = scene.window.toMinutes();
        if (Duration.between(first, now).compareTo(scene.window) > 0) {
            return "%s被动回复窗口（%d 分钟）已过，msg_id 已失效：本次发送大概率被平台拒绝，可改发主动消息"
                    .formatted(scene.label, minutes);
        }
        if (count > scene.maxReplies) {
            return "%s同一消息已回复 %d 次，超过平台上限 %d 次：本次发送大概率被拒（同一 msg_id+msg_seq 不可重复）"
                    .formatted(scene.label, count, scene.maxReplies);
        }
        if (count == scene.maxReplies) {
            return "%s已用满平台允许的 %d 次被动回复（%d 分钟内），下一次回复会被拒"
                    .formatted(scene.label, scene.maxReplies, minutes);
        }
        return null;
    }
}
