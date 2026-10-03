package love.aira.kohaku.handler;

import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 群内 @机器人 消息处理器示例：回复内容并消费事件（回复为异步副作用，见 {@link HandlerResult#consumed}）。 */
@Component
@Order(20)
public class EchoGroupHandler implements BotEventHandler<GroupAtMessageCreateEvent> {

    private static final Logger log = LoggerFactory.getLogger(EchoGroupHandler.class);

    private final BotReplies replies;

    public EchoGroupHandler(BotReplies replies) {
        this.replies = replies;
    }

    @Override
    public Class<GroupAtMessageCreateEvent> eventType() {
        return GroupAtMessageCreateEvent.class;
    }

    @Override
    public HandlerResult handle(GroupAtMessageCreateEvent event) {
        GroupMessage message = event.payload();
        String content = message.content();
        if (content != null && content.trim().startsWith("/")) {
            return HandlerResult.IGNORED;   // 命令交给对应功能（如 /card）处理
        }
        log.info("收到群消息 group={} member={} content={}", message.groupOpenid(), message.author().memberOpenid(),
                content);
        // 回复是耗时上行：交给框架的 worker 线程池异步执行
        return HandlerResult.consumed(() -> replies.text(event, "echo: " + message.content()));
    }
}
