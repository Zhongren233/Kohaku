package love.aira.kohaku.gateway.event;

import org.springframework.context.ApplicationEvent;

/** 所有网关事件的公共类型，业务侧可用 {@code @EventListener} 监听 {@code BotEvent} 或具体子类。 */
public abstract class BotEvent extends ApplicationEvent {

    private final long seq;

    protected BotEvent(Object source, long seq) {
        super(source);
        this.seq = seq;
    }

    /** 事件对应的网关序号 s，未携带序号时为 0。 */
    public long seq() {
        return seq;
    }
}
