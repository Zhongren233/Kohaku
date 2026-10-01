package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 创建私信会话请求（{@code POST /users/@me/dms}）：机器人与同一频道内的成员建立私信会话。
 *
 * @param recipientId   接收者 id
 * @param sourceGuildId 源频道 id（机器人与用户需有共同频道）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DmsSessionRequest(String recipientId, String sourceGuildId) {

    public static DmsSessionRequest of(String recipientId, String sourceGuildId) {
        return new DmsSessionRequest(recipientId, sourceGuildId);
    }
}
