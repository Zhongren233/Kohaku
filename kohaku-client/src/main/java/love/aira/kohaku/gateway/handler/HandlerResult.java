package love.aira.kohaku.gateway.handler;

/** 事件处理器的处理结果。 */
public enum HandlerResult {

    /** 已成功消费该事件：不再执行后续处理器；观察者通道不受影响（事件仍会发布为容器事件）。 */
    CONSUMED,

    /** 忽略该事件：继续执行下一个处理器；观察者通道不受影响（事件同样会发布为容器事件）。 */
    IGNORED
}
