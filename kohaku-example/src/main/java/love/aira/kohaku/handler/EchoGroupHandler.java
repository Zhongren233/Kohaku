package love.aira.kohaku.handler;

import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 群内 @机器人 消息处理器示例：回复内容并消费事件。 */
@Component
@Order(20)
public class EchoGroupHandler implements BotEventHandler<GroupAtMessageCreateEvent> {

    private static final Logger log = LoggerFactory.getLogger(EchoGroupHandler.class);

    private final QqMessageApi messages;

    public EchoGroupHandler(QqMessageApi messages) {
        this.messages = messages;
    }

    @Override
    public Class<GroupAtMessageCreateEvent> eventType() {
        return GroupAtMessageCreateEvent.class;
    }

    @Override
    public HandlerResult handle(GroupAtMessageCreateEvent event) {
        GroupMessage message = event.payload();
        log.info("收到群消息 group={} member={} content={}", message.groupOpenid(), message.author().memberOpenid(),
                message.content());
        messages.sendToGroup(message.groupOpenid(),
                SendMessageRequest.text("echo: " + message.content()).replyingTo(message.id(), 1));
        return HandlerResult.CONSUMED;
    }
}
