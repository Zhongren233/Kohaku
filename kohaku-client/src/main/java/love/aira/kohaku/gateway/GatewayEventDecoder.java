package love.aira.kohaku.gateway;

import java.util.Map;
import love.aira.kohaku.gateway.event.GuildCreateEvent;
import love.aira.kohaku.gateway.event.GuildUpdateEvent;
import love.aira.kohaku.gateway.event.GuildDeleteEvent;
import love.aira.kohaku.gateway.event.ChannelCreateEvent;
import love.aira.kohaku.gateway.event.ChannelUpdateEvent;
import love.aira.kohaku.gateway.event.ChannelDeleteEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupMessageCreateEvent;
import love.aira.kohaku.gateway.event.FriendAddEvent;
import love.aira.kohaku.gateway.event.FriendDelEvent;
import love.aira.kohaku.gateway.event.C2cMsgRejectEvent;
import love.aira.kohaku.gateway.event.C2cMsgReceiveEvent;
import love.aira.kohaku.gateway.event.GroupAddRobotEvent;
import love.aira.kohaku.gateway.event.GroupDelRobotEvent;
import love.aira.kohaku.gateway.event.GroupMsgRejectEvent;
import love.aira.kohaku.gateway.event.GroupMsgReceiveEvent;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.AtMessageCreateEvent;
import love.aira.kohaku.gateway.event.MessageCreateEvent;
import love.aira.kohaku.gateway.event.DirectMessageCreateEvent;
import love.aira.kohaku.gateway.event.MessageAuditPassEvent;
import love.aira.kohaku.gateway.event.MessageAuditRejectEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.C2cMsgReceive;
import love.aira.kohaku.gateway.event.model.C2cMsgReject;
import love.aira.kohaku.gateway.event.model.Channel;
import love.aira.kohaku.gateway.event.model.ChannelMessage;
import love.aira.kohaku.gateway.event.model.FriendAdd;
import love.aira.kohaku.gateway.event.model.FriendDel;
import love.aira.kohaku.gateway.event.model.GroupAddRobot;
import love.aira.kohaku.gateway.event.model.GroupDelRobot;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.event.model.GroupMsgReceive;
import love.aira.kohaku.gateway.event.model.GroupMsgReject;
import love.aira.kohaku.gateway.event.model.Guild;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.gateway.event.model.MessageAudited;
import love.aira.kohaku.gateway.event.BotDispatchEvent;
import love.aira.kohaku.support.SnakeCaseMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 网关事件反序列化：把 Dispatch（op=0）的 {@code t} + {@code d} 变成强类型事件。
 *
 * <p>收录了官方文档定义的全部事件（单聊/群聊消息、群与好友生命周期、频道与子频道、频道消息与私信、
 * 消息审核、互动事件）。未收录的事件名返回 {@code null}，调用方回落到原始 {@link BotDispatchEvent}；
 * 事件体与文档不符时同样回落，避免因个别字段异常打断长连接。
 */
public final class GatewayEventDecoder {

    private static final Logger log = LoggerFactory.getLogger(GatewayEventDecoder.class);

    private final ObjectMapper mapper;

    public GatewayEventDecoder(ObjectMapper mapper) {
        this.mapper = SnakeCaseMappers.of(mapper);
    }

    /** 按事件名解码；未知事件、空事件体或解析失败时返回 null。 */
    public BotDispatchEvent decode(long seq, String type, JsonNode data) {
        if (type == null || data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        try {
            return switch (type) {
                case GuildCreateEvent.TYPE -> new GuildCreateEvent(seq, data, payload(data, Guild.class));
                case GuildUpdateEvent.TYPE -> new GuildUpdateEvent(seq, data, payload(data, Guild.class));
                case GuildDeleteEvent.TYPE -> new GuildDeleteEvent(seq, data, payload(data, Guild.class));
                case ChannelCreateEvent.TYPE -> new ChannelCreateEvent(seq, data, payload(data, Channel.class));
                case ChannelUpdateEvent.TYPE -> new ChannelUpdateEvent(seq, data, payload(data, Channel.class));
                case ChannelDeleteEvent.TYPE -> new ChannelDeleteEvent(seq, data, payload(data, Channel.class));
                case C2cMessageCreateEvent.TYPE -> new C2cMessageCreateEvent(seq, data, payload(data, C2cMessage.class));
                case GroupAtMessageCreateEvent.TYPE -> new GroupAtMessageCreateEvent(seq, data, payload(data, GroupMessage.class));
                case GroupMessageCreateEvent.TYPE -> new GroupMessageCreateEvent(seq, data, payload(data, GroupMessage.class));
                case FriendAddEvent.TYPE -> new FriendAddEvent(seq, data, payload(data, FriendAdd.class));
                case FriendDelEvent.TYPE -> new FriendDelEvent(seq, data, payload(data, FriendDel.class));
                case C2cMsgRejectEvent.TYPE -> new C2cMsgRejectEvent(seq, data, payload(data, C2cMsgReject.class));
                case C2cMsgReceiveEvent.TYPE -> new C2cMsgReceiveEvent(seq, data, payload(data, C2cMsgReceive.class));
                case GroupAddRobotEvent.TYPE -> new GroupAddRobotEvent(seq, data, payload(data, GroupAddRobot.class));
                case GroupDelRobotEvent.TYPE -> new GroupDelRobotEvent(seq, data, payload(data, GroupDelRobot.class));
                case GroupMsgRejectEvent.TYPE -> new GroupMsgRejectEvent(seq, data, payload(data, GroupMsgReject.class));
                case GroupMsgReceiveEvent.TYPE -> new GroupMsgReceiveEvent(seq, data, payload(data, GroupMsgReceive.class));
                case InteractionCreateEvent.TYPE -> new InteractionCreateEvent(seq, data, payload(data, InteractionCreate.class));
                case AtMessageCreateEvent.TYPE -> new AtMessageCreateEvent(seq, data, payload(data, ChannelMessage.class));
                case MessageCreateEvent.TYPE -> new MessageCreateEvent(seq, data, payload(data, ChannelMessage.class));
                case DirectMessageCreateEvent.TYPE -> new DirectMessageCreateEvent(seq, data, payload(data, ChannelMessage.class));
                case MessageAuditPassEvent.TYPE -> new MessageAuditPassEvent(seq, data, payload(data, MessageAudited.class));
                case MessageAuditRejectEvent.TYPE -> new MessageAuditRejectEvent(seq, data, payload(data, MessageAudited.class));
                default -> null;
            };
        } catch (JacksonException e) {
            log.warn("cannot decode {} payload, falling back to raw event: {}", type, e.getMessage());
            return null;
        }
    }

    /** 手动解析任意事件体（未收录事件的逃生门）。 */
    public <P> P payload(JsonNode data, Class<P> payloadType) {
        return mapper.treeToValue(data, payloadType);
    }
}
