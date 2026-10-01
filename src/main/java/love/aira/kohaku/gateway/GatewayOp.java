package love.aira.kohaku.gateway;

/** Gateway 协议的 OpCode，见 QQ 机器人开放平台 WebSocket 文档。 */
public final class GatewayOp {

    /** 事件推送 */
    public static final int DISPATCH = 0;
    /** 客户端发送心跳 */
    public static final int HEARTBEAT = 1;
    /** 客户端鉴权登录 */
    public static final int IDENTIFY = 2;
    /** 客户端恢复登录态 */
    public static final int RESUME = 6;
    /** 服务端通知客户端重新连接 */
    public static final int RECONNECT = 7;
    /** 服务端判定 Identify/Resume 参数有误、session 无效 */
    public static final int INVALID_SESSION = 9;
    /** 网关下发心跳周期 */
    public static final int HELLO = 10;
    /** 心跳确认 */
    public static final int HEARTBEAT_ACK = 11;

    private GatewayOp() {
    }
}
