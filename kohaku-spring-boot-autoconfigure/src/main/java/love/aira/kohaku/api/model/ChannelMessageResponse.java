package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * 频道子频道消息与频道私信的发送响应（Message 对象，仅列出文档给出的字段）。
 *
 * @param embeds 文档示例为 {@code [{}]} 未给出结构，按原样透传
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChannelMessageResponse(String id, String channelId, String guildId, String content, String timestamp,
                                     Boolean tts, Boolean mentionEveryone, Author author, JsonNode embeds,
                                     Boolean pinned, Integer type, Integer flags) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(String id, String username, String avatar, Boolean bot) {
    }
}
