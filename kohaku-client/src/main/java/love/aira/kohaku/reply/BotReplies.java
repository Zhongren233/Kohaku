package love.aira.kohaku.reply;

import java.time.Clock;
import love.aira.kohaku.api.QqChannelMessageApi;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.model.ChannelMessageRequest;
import love.aira.kohaku.api.model.ChannelMessageResponse;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.api.model.SendMessageResponse;
import love.aira.kohaku.gateway.event.AtMessageCreateEvent;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.DirectMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupMessageCreateEvent;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.MessageCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.reply.PassiveBudget.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一的被动回复入口：从**入站事件**自动解析目标与被动标记，业务侧不再自己拼 openid、不再手管 msg_seq。
 *
 * <pre>{@code
 * replies.text(event, "pong");                                    // 纯文本
 * replies.markdown(event, "# 标题\n正文", keyboard);               // 带按钮（键盘必须用 markdown 承载）
 * replies.send(event, SendMessageRequest.image(url));              // 逃生门：自定义请求，仍自动补目标与被动标记
 * replies.sendToChannel(event, ChannelMessageRequest.text("hi"));  // 频道 / 频道私信（请求体与单聊/群聊不同）
 * }</pre>
 *
 * <p>自动处理：
 * <ul>
 *   <li><b>目标</b>：单聊取 {@code author.user_openid}、群聊取 {@code group_openid}、互动按 {@code chat_type}
 *       选单聊/群聊；频道/私信取 {@code channel_id} / {@code guild_id}；</li>
 *   <li><b>被动标记</b>：消息事件用 {@code msg_id + msg_seq}（{@code msg_seq} 自动递增，避免"相同
 *       msg_id+msg_seq 重复发送"失败；单聊最多 4 次、群聊 5 次）；互动事件用**最外层** {@code event_id}；</li>
 *   <li>调用方已自行指定 {@code msg_id}/{@code event_id} 时不覆盖。</li>
 * </ul>
 *
 * <p>不支持回复的事件（READY/RESUMED、好友/群生命周期等）抛出 {@link IllegalArgumentException}。
 */
public class BotReplies {

    private static final Logger log = LoggerFactory.getLogger(BotReplies.class);

    private final QqMessageApi messages;
    private final QqChannelMessageApi channelMessages;

    /** 被动回复的窗口/次数记账：撞上平台限制之前先告警（平台只会直接拒绝）。 */
    private final PassiveBudget budget = new PassiveBudget(Clock.systemUTC());

    /** 只回复单聊/群聊时使用。 */
    public BotReplies(QqMessageApi messages) {
        this(messages, null);
    }

    public BotReplies(QqMessageApi messages, QqChannelMessageApi channelMessages) {
        this.messages = messages;
        this.channelMessages = channelMessages;
    }

    /** 回复纯文本。 */
    public SendMessageResponse text(BotEvent event, String content) {
        return send(event, SendMessageRequest.text(content));
    }

    /** 回复 markdown（**带按钮时必须用这个**：键盘只在 markdown 消息上渲染）。 */
    public SendMessageResponse markdown(BotEvent event, String markdown) {
        return send(event, SendMessageRequest.markdown(markdown));
    }

    /** 回复带内嵌键盘的 markdown。 */
    public SendMessageResponse markdown(BotEvent event, String markdown, Keyboard keyboard) {
        SendMessageRequest request = SendMessageRequest.markdown(markdown);
        return send(event, keyboard == null ? request : request.withKeyboard(keyboard));
    }

    /** 回复自定义请求（单聊/群聊/互动）：自动补齐目标与被动标记。 */
    public SendMessageResponse send(BotEvent event, SendMessageRequest request) {
        return switch (event) {
            case C2cMessageCreateEvent c2c -> {
                C2cMessage message = c2c.payload();
                yield messages.sendToUser(message.author().userOpenid(),
                        passiveMessage(request, message.id(), Scene.C2C));
            }
            case GroupAtMessageCreateEvent group -> {
                GroupMessage message = group.payload();
                yield messages.sendToGroup(message.groupOpenid(),
                        passiveMessage(request, message.id(), Scene.GROUP));
            }
            case GroupMessageCreateEvent group -> {
                GroupMessage message = group.payload();
                yield messages.sendToGroup(message.groupOpenid(),
                        passiveMessage(request, message.id(), Scene.GROUP));
            }
            case InteractionCreateEvent interaction -> {
                InteractionCreate payload = interaction.payload();
                SendMessageRequest replied = passiveEvent(request, interaction.eventId());
                if (payload != null && payload.userOpenid() != null) {
                    yield messages.sendToUser(payload.userOpenid(), replied);
                }
                yield messages.sendToGroup(payload.groupOpenid(), replied);
            }
            default -> throw new IllegalArgumentException("该事件不支持单聊/群聊回复: "
                    + event.getClass().getSimpleName()
                    + "（频道/私信请用 sendToChannel；READY/RESUMED 等事件不能回复）");
        };
    }

    /**
     * 频道子频道消息与频道私信的回复（请求体与单聊/群聊不同，故单列）：自动选目标并补被动标记
     * （消息事件用 {@code msg_id}、频道场景的互动事件用 {@code event_id}）。
     */
    public ChannelMessageResponse sendToChannel(BotEvent event, ChannelMessageRequest request) {
        return switch (event) {
            case AtMessageCreateEvent at -> channelMessages(at)
                    .sendToChannel(at.payload().channelId(), passiveChannel(request, at.payload().id()));
            case MessageCreateEvent channel -> channelMessages(channel)
                    .sendToChannel(channel.payload().channelId(), passiveChannel(request, channel.payload().id()));
            case DirectMessageCreateEvent direct -> channelMessages(direct)
                    .sendToDms(direct.payload().guildId(), passiveChannel(request, direct.payload().id()));
            case InteractionCreateEvent interaction -> channelMessages(interaction)
                    .sendToChannel(interaction.payload().channelId(),
                            passiveChannelEvent(request, interaction.eventId()));
            default -> throw new IllegalArgumentException("该事件不支持频道/私信回复: "
                    + event.getClass().getSimpleName() + "（单聊/群聊请用 send）");
        };
    }

    private QqChannelMessageApi channelMessages(BotEvent event) {
        if (channelMessages == null) {
            throw new IllegalStateException("频道/私信回复需要 QqChannelMessageApi，请在构造 BotReplies 时提供（事件: "
                    + event.getClass().getSimpleName() + "）");
        }
        return channelMessages;
    }

    /**
     * 消息事件的被动标记：{@code msg_id + 自增 msg_seq}；调用方已指定则不覆盖。
     * 顺带按平台限制（窗口/次数）提前告警。
     */
    private SendMessageRequest passiveMessage(SendMessageRequest request, String messageId, Scene scene) {
        if (request.msgId() != null || request.eventId() != null) {
            return request;
        }
        PassiveBudget.Usage usage = budget.record(messageId, scene);
        warn(messageId, usage);
        return request.replyingTo(messageId, usage.sequence());
    }

    /** 互动事件的被动标记：最外层事件 id 作 {@code event_id}；调用方已指定则不覆盖。 */
    private SendMessageRequest passiveEvent(SendMessageRequest request, String eventId) {
        return request.msgId() != null || request.eventId() != null ? request : request.replyingToEvent(eventId);
    }

    private ChannelMessageRequest passiveChannel(ChannelMessageRequest request, String messageId) {
        if (request.msgId() != null || request.eventId() != null) {
            return request;
        }
        warn(messageId, budget.record(messageId, Scene.GUILD));   // 频道无 msg_seq，只查窗口
        return request.replyingTo(messageId);
    }

    private void warn(String messageId, PassiveBudget.Usage usage) {
        if (usage.warning() != null) {
            log.warn("{}（msg_id={}）", usage.warning(), messageId);
        }
    }

    private ChannelMessageRequest passiveChannelEvent(ChannelMessageRequest request, String eventId) {
        return request.msgId() != null || request.eventId() != null ? request : request.replyingToEvent(eventId);
    }

}
