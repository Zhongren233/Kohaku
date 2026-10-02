package love.aira.kohaku.observer;

import love.aira.kohaku.gateway.event.BotDispatchEvent;
import love.aira.kohaku.gateway.event.BotEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 观察者示例：打印机器人收到的**每一个**事件。
 *
 * <p>这里不是 {@code BotEventHandler}（不参与有序处理链、也没有 CONSUMED 的概念），而是
 * {@code @EventListener} 观察者通道：{@code BotEvent} 参数会收到 READY / RESUMED 与全部 dispatch 事件，
 * 且**与处理链互不影响** —— 即使事件已被某个处理器消费（CONSUMED），这里依然收得到。
 *
 * <p>本方法在网关读循环线程（{@code qq-gateway-io}）上同步执行：示例只做打印。真实项目里若要做
 * 落库、指标上报等耗时操作，请加 {@code @Async} 或自行投递到线程池，否则会顶住心跳与后续事件。
 */
@Component
public class EventLogger {

    private static final Logger log = LoggerFactory.getLogger(EventLogger.class);

    @EventListener
    public void onEvent(BotEvent event) {
        if (event instanceof BotDispatchEvent dispatch) {
            log.info("event {} seq={} event_id={}", dispatch.type(), dispatch.seq(), dispatch.eventId());
            // 需要完整事件体时打开 DEBUG：logging.level.love.aira.kohaku.observer.EventLogger=DEBUG
            if (log.isDebugEnabled()) {
                log.debug("event {} payload={}", dispatch.type(), dispatch.data());
            }
        } else {
            log.info("event {} seq={}", event.getClass().getSimpleName(), event.seq());
        }
    }
}
