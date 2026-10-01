package love.aira.kohaku.feature;

import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.api.model.SendMessageResponse;

/** 按互动事件所在场景把回复发到正确目标，并自动带上 {@code event_id}（被动回复）。 */
public final class InteractionReplies {

    private InteractionReplies() {
    }

    /**
     * 回复一次按钮点击：单聊走 {@code /v2/users/{openid}/messages}，群聊走 {@code /v2/groups/{openid}/messages}，
     * 均以互动事件 id 作为 {@code event_id}（单聊 60 分钟 / 群聊 5 分钟内有效）。
     *
     * @throws IllegalArgumentException 无法确定目标（例如频道场景，请改用 {@code QqChannelMessageApi}）
     */
    public static SendMessageResponse reply(QqMessageApi messages, ButtonContext context, SendMessageRequest request) {
        SendMessageRequest passive = context.replyingTo(request);
        if (context.isGroup() && context.groupOpenid() != null) {
            return messages.sendToGroup(context.groupOpenid(), passive);
        }
        if (context.userOpenid() != null) {
            return messages.sendToUser(context.userOpenid(), passive);
        }
        throw new IllegalArgumentException("无法确定回复目标（chatType=" + context.chatType()
                + ", scene=" + context.scene() + "）；频道场景请使用 QqChannelMessageApi");
    }
}
