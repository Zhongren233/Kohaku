package love.aira.kohaku.api;

import java.nio.file.Path;
import java.util.Map;
import love.aira.kohaku.api.model.ChannelMessageRequest;
import love.aira.kohaku.api.model.ChannelMessageResponse;
import love.aira.kohaku.api.model.DmsSessionRequest;
import love.aira.kohaku.api.model.DmsSessionResponse;
import org.springframework.stereotype.Component;

/**
 * 频道消息与频道私信接口。
 *
 * <ul>
 *   <li>{@code POST /channels/{channel_id}/messages} 发送子频道消息（JSON，或 form-data 带 file_image）</li>
 *   <li>{@code DELETE /channels/{channel_id}/messages/{message_id}?hidetip=} 撤回子频道消息</li>
 *   <li>{@code POST /users/@me/dms} 创建私信会话</li>
 *   <li>{@code POST /dms/{guild_id}/messages} 发送私信（参数与发送子频道消息一致）</li>
 *   <li>{@code DELETE /dms/{guild_id}/messages/{message_id}?hidetip=} 撤回私信</li>
 * </ul>
 *
 * <p>发送接口要求机器人 WebSocket 保持在线；被动回复有效期为 5 分钟；撤回仅限机器人自己发送的消息。
 * 这几组接口需私域机器人权限。
 */
@Component
public class QqChannelMessageApi {

    private final QqOpenApiClient api;

    public QqChannelMessageApi(QqOpenApiClient api) {
        this.api = api;
    }

    /** 发送子频道消息。 */
    public ChannelMessageResponse sendToChannel(String channelId, ChannelMessageRequest request) {
        return api.post("/channels/" + PathSegments.encode("channel_id", channelId) + "/messages", request,
                ChannelMessageResponse.class);
    }

    /** 以 form-data 方式发送子频道消息并直接上传图片文件（对应文档的 file_image 参数）。 */
    public ChannelMessageResponse sendImageToChannel(String channelId, ChannelMessageRequest request, Path image) {
        Map<String, Object> fields = api.formFields(request);
        return api.postMultipart("/channels/" + PathSegments.encode("channel_id", channelId) + "/messages", fields,
                "file_image", image, ChannelMessageResponse.class);
    }

    /** 撤回子频道消息。 */
    public void recallFromChannel(String channelId, String messageId) {
        recallFromChannel(channelId, messageId, false);
    }

    /** 撤回子频道消息，{@code hideTip} 控制是否隐藏「消息已撤回」提示小灰条。 */
    public void recallFromChannel(String channelId, String messageId, boolean hideTip) {
        api.delete("/channels/" + PathSegments.encode("channel_id", channelId) + "/messages/" + PathSegments.encode("message_id", messageId)
                + "?hidetip=" + hideTip);
    }

    /** 创建私信会话，返回的 guild_id 用于后续发送/撤回私信。 */
    public DmsSessionResponse createDmsSession(String recipientId, String sourceGuildId) {
        return api.post("/users/@me/dms", DmsSessionRequest.of(recipientId, sourceGuildId),
                DmsSessionResponse.class);
    }

    /** 发送私信（路径参数是私信会话的 guild_id）。 */
    public ChannelMessageResponse sendToDms(String dmsGuildId, ChannelMessageRequest request) {
        return api.post("/dms/" + PathSegments.encode("guild_id", dmsGuildId) + "/messages", request,
                ChannelMessageResponse.class);
    }

    /** 以 form-data 方式发送私信并直接上传图片文件。 */
    public ChannelMessageResponse sendImageToDms(String dmsGuildId, ChannelMessageRequest request, Path image) {
        Map<String, Object> fields = api.formFields(request);
        return api.postMultipart("/dms/" + PathSegments.encode("guild_id", dmsGuildId) + "/messages", fields,
                "file_image", image, ChannelMessageResponse.class);
    }

    /** 撤回私信。 */
    public void recallFromDms(String dmsGuildId, String messageId) {
        recallFromDms(dmsGuildId, messageId, false);
    }

    /** 撤回私信，{@code hideTip} 控制是否隐藏提示小灰条。 */
    public void recallFromDms(String dmsGuildId, String messageId, boolean hideTip) {
        api.delete("/dms/" + PathSegments.encode("guild_id", dmsGuildId) + "/messages/" + PathSegments.encode("message_id", messageId)
                + "?hidetip=" + hideTip);
    }

}
