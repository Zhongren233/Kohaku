package love.aira.kohaku.handler;

import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 单聊消息处理器示例：把收到的文本原样回复，并返回 {@link HandlerResult#CONSUMED} 终止后续处理器。
 *
 * <p>执行顺序：本类 {@code @Order(10)} 在群聊处理器之前；也可用配置 {@code kohaku.qq.handler-order}
 * 按 Bean 名称显式声明顺序（未列出的处理器排在其后）。
 */
@Component
@Order(10)
public class EchoC2cHandler implements BotEventHandler<C2cMessageCreateEvent> {

    private static final Logger log = LoggerFactory.getLogger(EchoC2cHandler.class);

    private final QqMessageApi messages;

    public EchoC2cHandler(QqMessageApi messages) {
        this.messages = messages;
    }

    @Override
    public Class<C2cMessageCreateEvent> eventType() {
        return C2cMessageCreateEvent.class;
    }

    @Override
    public HandlerResult handle(C2cMessageCreateEvent event) {
        C2cMessage message = event.payload();
        String content = message.content();
        if (content != null && content.trim().startsWith("/")) {
            return HandlerResult.IGNORED;   // 命令交给对应功能（如 /card）处理
        }
        String openid = message.author().userOpenid();
        log.info("收到单聊消息 openid={} msg_idx={} content={}", openid, message.messageScene().messageIndex(),
                message.content());
        // 被动回复：携带 msg_id，单聊 60 分钟内有效、同一消息最多回复 4 次（多次回复需递增 msg_seq）
        messages.sendToUser(openid, SendMessageRequest.text("echo: " + message.content()).replyingTo(message.id(), 1));
        return HandlerResult.CONSUMED;
    }
}
