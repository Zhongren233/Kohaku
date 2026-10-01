package love.aira.kohaku.gateway.event;

/** 恢复登录态成功（OpCode 0 / RESUMED），此前遗漏的事件已补发完毕。 */
public class BotResumedEvent extends BotEvent {

    public BotResumedEvent(long seq) {
        super(seq);
    }
}
