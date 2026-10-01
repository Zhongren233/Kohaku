package love.aira.kohaku.gateway.event;

import java.util.List;

/**
 * 鉴权成功（OpCode 0 / READY），携带会话 id，需持久化以便断线后 Resume。
 *
 * @param sessionId 网关分配的会话 id
 * @param user      机器人自身信息
 * @param shard     当前连接的分片 [index, total]
 */
public class BotReadyEvent extends BotEvent {

    private final String sessionId;
    private final BotUser user;
    private final List<Integer> shard;

    public BotReadyEvent(Object source, long seq, String sessionId, BotUser user, List<Integer> shard) {
        super(source, seq);
        this.sessionId = sessionId;
        this.user = user;
        this.shard = List.copyOf(shard);
    }

    public String sessionId() {
        return sessionId;
    }

    public BotUser user() {
        return user;
    }

    public List<Integer> shard() {
        return shard;
    }
}
