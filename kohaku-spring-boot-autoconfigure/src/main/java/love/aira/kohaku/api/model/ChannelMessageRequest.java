package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

/**
 * 频道子频道消息与频道私信的发送请求体。
 *
 * <p>至少需要 {@code content}、{@code embed}、{@code ark}、{@code image}（或 form-data 的 file_image）、
 * {@code markdown} 之一，否则无法下发。填了 {@code markdown} 时 {@code content} 必须为空。
 *
 * <p>{@code embed} 与 {@code ark} 的结构见「消息类型」文档中的 MessageEmbed/MessageArk 模板，
 * 这里按平台原样透传（不做结构校验）。
 *
 * @param image      图片 URL，平台会转存后下发
 * @param msgId      被动回复的消息 id（Message.id），填入即为被动消息，频道有效期 5 分钟
 * @param eventId    被动回复的事件 id，与 msgId 二选一
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelMessageRequest(String content, JsonNode embed, JsonNode ark, MessageReference messageReference,
                                    String image, String msgId, String eventId, MarkdownMessage markdown) {

    public static ChannelMessageRequest text(String content) {
        return new ChannelMessageRequest(content, null, null, null, null, null, null, null);
    }

    public static ChannelMessageRequest image(String imageUrl) {
        return new ChannelMessageRequest(null, null, null, null, imageUrl, null, null, null);
    }

    /** Markdown 消息；频道场景需内邀开通模板权限。 */
    public static ChannelMessageRequest markdown(MarkdownMessage markdown) {
        return new ChannelMessageRequest(null, null, null, null, null, null, null, markdown);
    }

    /** Ark 消息（结构见消息类型文档）。 */
    public static ChannelMessageRequest ark(JsonNode ark) {
        return new ChannelMessageRequest(null, null, ark, null, null, null, null, null);
    }

    /** Embed 消息（一种特殊的 ark）。 */
    public static ChannelMessageRequest embed(JsonNode embed) {
        return new ChannelMessageRequest(null, embed, null, null, null, null, null, null);
    }

    public ChannelMessageRequest replyingTo(String msgId) {
        return new ChannelMessageRequest(content, embed, ark, messageReference, image, msgId, eventId, markdown);
    }

    public ChannelMessageRequest replyingToEvent(String eventId) {
        return new ChannelMessageRequest(content, embed, ark, messageReference, image, msgId, eventId, markdown);
    }

    /** 引用回复某条消息。 */
    public ChannelMessageRequest quoting(String messageId) {
        return new ChannelMessageRequest(content, embed, ark, MessageReference.of(messageId), image, msgId, eventId,
                markdown);
    }
}
