package love.aira.kohaku.api;

import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.api.model.SendMessageResponse;
import love.aira.kohaku.api.model.StreamMessageRequest;
import love.aira.kohaku.api.model.StreamMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QQ 单聊与群聊消息接口。
 *
 * <ul>
 *   <li>{@code POST /v2/users/{user_openid}/messages} 发送单聊消息</li>
 *   <li>{@code POST /v2/users/{user_openid}/stream_messages} 流式发送单聊消息</li>
 *   <li>{@code DELETE /v2/users/{user_openid}/messages/{message_id}} 撤回单聊消息</li>
 *   <li>{@code POST /v2/groups/{group_openid}/messages} 发送群聊消息</li>
 *   <li>{@code DELETE /v2/groups/{group_openid}/messages/{message_id}} 撤回群聊消息</li>
 * </ul>
 *
 * <p>开放平台把 OpenID 作为不透明路径参数，这里统一做路径段转义，避免消息 ID 中的特殊字符破坏路径。
 */
public class QqMessageApi {

    private static final Logger log = LoggerFactory.getLogger(QqMessageApi.class);

    private final QqOpenApiClient api;

    public QqMessageApi(QqOpenApiClient api) {
        this.api = api;
    }

    /** 发送单聊消息；回复用户时请对 {@link SendMessageRequest#replyingTo(String)} 的结果继续构造。 */
    public SendMessageResponse sendToUser(String userOpenid, SendMessageRequest request) {
        warnIfKeyboardOnPlainText(request);
        return api.post("/v2/users/" + PathSegments.encode("user_openid", userOpenid) + "/messages", request,
                SendMessageResponse.class);
    }

    /** 流式发送单聊消息；首个分片返回的 {@code id} 作为后续分片的 stream_msg_id。 */
    public StreamMessageResponse sendStreamToUser(String userOpenid, StreamMessageRequest request) {
        return api.post("/v2/users/" + PathSegments.encode("user_openid", userOpenid) + "/stream_messages", request,
                StreamMessageResponse.class);
    }

    /** 撤回机器人发给该用户的消息（超过 2 分钟不可撤回）。 */
    public void recallFromUser(String userOpenid, String messageId) {
        api.delete("/v2/users/" + PathSegments.encode("user_openid", userOpenid) + "/messages/"
                + PathSegments.encode("message_id", messageId));
    }

    /** 发送群聊消息。 */
    public SendMessageResponse sendToGroup(String groupOpenid, SendMessageRequest request) {
        warnIfKeyboardOnPlainText(request);
        return api.post("/v2/groups/" + PathSegments.encode("group_openid", groupOpenid) + "/messages", request,
                SendMessageResponse.class);
    }

    /**
     * 实测：客户端只在 markdown 消息（{@code msg_type=2}）上渲染自定义键盘，纯文本带键盘会被静默丢弃，
     * 这里给出告警避免"发了但看不到按钮"的困惑。
     */
    private static void warnIfKeyboardOnPlainText(SendMessageRequest request) {
        Integer msgType = request.msgType();
        if (request.keyboard() != null && msgType != null && msgType != SendMessageRequest.TYPE_MARKDOWN) {
            log.warn("消息带 keyboard 但 msg_type={}（非 markdown），客户端不会渲染自定义键盘；"
                    + "请改用 SendMessageRequest.markdown(...)", msgType);
        }
    }

    /** 撤回群聊消息；机器人为群管理员时也可撤回普通成员的消息（超过 2 分钟不可撤回）。 */
    public void recallFromGroup(String groupOpenid, String messageId) {
        api.delete("/v2/groups/" + PathSegments.encode("group_openid", groupOpenid) + "/messages/"
                + PathSegments.encode("message_id", messageId));
    }

}
