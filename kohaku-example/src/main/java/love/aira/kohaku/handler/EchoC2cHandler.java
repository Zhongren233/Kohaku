package love.aira.kohaku.handler;

import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 单聊消息处理器示例：把收到的文本原样回复。回复以 {@link HandlerResult#consumed(Runnable)} 声明为
 * 异步副作用（交给框架的 worker 线程池），路由判定同步、命中即终止后续处理器。
 *
 * <p>执行顺序：本类 {@code @Order(10)} 在群聊处理器之前；也可用配置 {@code kohaku.qq.handler-order}
 * 按 Bean 名称显式声明顺序（未列出的处理器排在其后）。
 */
@Component
@Order(10)
public class EchoC2cHandler implements BotEventHandler<C2cMessageCreateEvent> {

    private static final Logger log = LoggerFactory.getLogger(EchoC2cHandler.class);

    private final BotReplies replies;

    public EchoC2cHandler(BotReplies replies) {
        this.replies = replies;
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
        // 被动回复是耗时上行（网络 + 可能的 token 刷新）：交给框架的 worker 线程池异步执行，
        // 路由判定保持同步（返回 consumed 即终止后续处理器）
        return HandlerResult.consumed(() -> replies.text(event, "echo: " + message.content()));
    }
}
