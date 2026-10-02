package love.aira.kohaku.interaction;

/**
 * 互动事件的应答时机。
 *
 * <p>平台要求 {@code type=11/12} 的互动必须应答,否则客户端一直 loading 到超时；而 {@code code}
 * 又代表处理结果（0 成功 / 1 操作失败 / 4 无权限 …），两者在时机上存在取舍：
 *
 * <ul>
 *   <li>{@link #IMMEDIATE}（默认）：命中按钮处理器后**先应答 code=0**，再去执行处理器。客户端立刻结束
 *       loading，不会因处理器耗时（如远程调用）而超时；代价是应答不再反映处理结果。</li>
 *   <li>{@link #AFTER_HANDLING}：先执行处理器再应答，{@code code} 反映真实结果（成功 0 / 抛异常 1），
 *       但处理器过慢时客户端仍可能先超时。</li>
 * </ul>
 */
public enum InteractionAckMode {

    /** 先应答再处理（默认）。 */
    IMMEDIATE,

    /** 先处理再按结果应答。 */
    AFTER_HANDLING
}
