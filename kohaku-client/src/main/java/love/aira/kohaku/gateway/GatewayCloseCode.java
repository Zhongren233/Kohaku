package love.aira.kohaku.gateway;

/**
 * WebSocket 错误码到处置方式的映射，严格对应开放平台文档的错误码表。
 *
 * <p>未在文档中列出的服务端错误码（含 4003/4004/4005 等）按文档“其他错误，请重新发起 identify”处理；
 * 而异常断开（无关闭帧、1000/1001/1006）保留 session 尝试 Resume——若服务端认为 session 已失效，
 * 会以 4006/4007 拒绝，届时再由本策略清 session 重新 Identify。
 */
public final class GatewayCloseCode {

    /** JDK WebSocket 在连接异常断开、未收到关闭帧时使用的占位码。 */
    public static final int ABNORMAL = -1;

    private GatewayCloseCode() {
    }

    public static ReconnectAction actionFor(int code) {
        if (code >= 4900 && code <= 4913) {
            // 内部错误，请重连（可重新 Identify）
            return ReconnectAction.IDENTIFY;
        }
        return switch (code) {
            // 无效 opcode / payload / shard / version / intent，intent 无权限，机器人下架或封禁
            case 4001, 4002, 4010, 4011, 4012, 4013, 4014, 4914, 4915 -> ReconnectAction.FATAL;
            // seq 错误、无效 session id
            case 4006, 4007 -> ReconnectAction.IDENTIFY;
            // 发送过快、连接过期
            case 4008, 4009 -> ReconnectAction.RESUME;
            // 异常断开：网络抖动、对端未发关闭帧
            case ABNORMAL, 1000, 1001, 1006 -> ReconnectAction.RESUME;
            default -> ReconnectAction.IDENTIFY;
        };
    }
}
