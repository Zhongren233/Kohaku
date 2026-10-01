package love.aira.kohaku.feature;

/**
 * 互动事件应答：{@link InteractionRouter} 在处理完按钮点击后调用它通知平台（否则客户端一直 loading 直到超时）。
 *
 * <p>Spring 下自动装配为 {@code QqInteractionApi::respond}；纯 Java 场景可自行实现或传 {@code null} 不自动应答。
 */
@FunctionalInterface
public interface InteractionResponder {

    /** 回调结果：成功。 */
    int CODE_SUCCESS = 0;
    /** 回调结果：操作失败（例如没有匹配的按钮处理器）。 */
    int CODE_FAILED = 1;

    /**
     * 回应一次互动事件。
     *
     * @param interactionId 事件体 {@code d.id}（不带事件名前缀）
     * @param code          {@link #CODE_SUCCESS} / {@link #CODE_FAILED} 等
     */
    void respond(String interactionId, int code);
}
