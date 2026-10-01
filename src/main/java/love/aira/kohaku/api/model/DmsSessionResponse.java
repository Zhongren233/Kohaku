package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 创建私信会话响应（DMS 对象）。
 *
 * @param guildId    私信会话关联的频道 id，后续发送/撤回私信都用它作为路径参数
 * @param channelId  私信会话关联的子频道 id
 * @param createTime 创建时间戳（文档示例为字符串）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DmsSessionResponse(String guildId, String channelId, String createTime) {
}
