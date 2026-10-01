package love.aira.kohaku.gateway;

/** 连接断开后的处置方式。 */
public enum ReconnectAction {

    /** 保留 session，重连后发送 OpCode 6 Resume。 */
    RESUME,

    /** 丢弃 session，重连后发送 OpCode 2 Identify 重新鉴权。 */
    IDENTIFY,

    /** 不可重试，停止连接（机器人被封禁/下架、协议错误等）。 */
    FATAL
}
