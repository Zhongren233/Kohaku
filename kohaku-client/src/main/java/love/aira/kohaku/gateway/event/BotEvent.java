package love.aira.kohaku.gateway.event;

/**
 * 所有网关事件的公共父类（纯 Java，非 Spring 宿主也能直接消费；Spring 侧由自动配置转发为容器事件，
 * 因此 {@code @EventListener} 用法不变）。
 */
public abstract class BotEvent {

    private final long seq;

    protected BotEvent(long seq) {
        this.seq = seq;
    }

    /** 事件对应的网关序号 s，未携带序号时为 0。 */
    public long seq() {
        return seq;
    }
}
