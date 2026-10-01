package love.aira.kohaku.gateway.handler;

/** 事件处理器的处理结果。 */
public enum HandlerResult {

    /** 已成功消费该事件：不再执行后续处理器（Spring 下也不会再发布为容器事件）。 */
    CONSUMED,

    /** 忽略该事件：继续执行下一个处理器；全部忽略时交给兜底消费者（Spring 下即发布容器事件）。 */
    IGNORED
}
