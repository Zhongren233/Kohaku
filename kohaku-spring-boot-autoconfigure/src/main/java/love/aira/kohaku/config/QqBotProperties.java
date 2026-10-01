package love.aira.kohaku.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * QQ 机器人开放平台配置。
 *
 * @param appId                  机器人 AppID
 * @param appSecret              机器人 AppSecret，用于换取 AccessToken
 * @param apiBaseUrl             开放平台 API 基地址（沙箱环境为 https://sandbox.api.sgroup.qq.com）
 * @param tokenUrl               AccessToken 获取地址
 * @param intents                事件订阅，多选 {@link QqIntent}，默认 {@code PUBLIC_GUILD_MESSAGES}
 *                               （频道内 @机器人 的消息）。基础权限仅 GUILDS、GUILD_MEMBERS、
 *                               PUBLIC_GUILD_MESSAGES，其余（如群/单聊 GROUP_AND_C2C_EVENT、私域 GUILD_MESSAGES）
 *                               需在开放平台申请；传了无权限的位网关会下发 OpCode 9 并以 4014 断开
 * @param shardIndex             当前连接的分片序号，从 0 开始
 * @param shardTotal             分片总数，单连接场景为 1
 * @param clientName             上报给网关的 $browser/$device 标识
 * @param autoStart              随 Spring 容器启动/停止网关连接
 * @param reconnectInitialDelay  重连退避起始延迟
 * @param reconnectMaxDelay      重连退避上限
 */
@ConfigurationProperties(prefix = "kohaku.qq")
public record QqBotProperties(
        String appId,
        String appSecret,
        @DefaultValue("https://api.sgroup.qq.com") String apiBaseUrl,
        @DefaultValue("https://bots.qq.com/app/getAppAccessToken") String tokenUrl,
        @DefaultValue("PUBLIC_GUILD_MESSAGES") List<QqIntent> intents,
        @DefaultValue("0") int shardIndex,
        @DefaultValue("1") int shardTotal,
        @DefaultValue("kohaku") String clientName,
        @DefaultValue("true") boolean autoStart,
        @DefaultValue("1s") Duration reconnectInitialDelay,
        @DefaultValue("60s") Duration reconnectMaxDelay) {

    public QqBotProperties {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("kohaku.qq.app-id is missing: 在本地 secrets/qq-bot.yaml 中填写，"
                    + "或设置环境变量 KOHAKU_QQ_APPID");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("kohaku.qq.app-secret is missing: 在本地 secrets/qq-bot.yaml 中填写，"
                    + "或设置环境变量 KOHAKU_QQ_APPSECRET");
        }
        if (intents == null || intents.isEmpty()) {
            throw new IllegalArgumentException(
                    "kohaku.qq.intents must not be empty, e.g. [PUBLIC_GUILD_MESSAGES] or [GUILDS, PUBLIC_GUILD_MESSAGES]");
        }
        intents = List.copyOf(intents);
        if (shardTotal < 1) {
            throw new IllegalArgumentException("kohaku.qq.shard-total must be >= 1, was " + shardTotal);
        }
        if (shardIndex < 0 || shardIndex >= shardTotal) {
            throw new IllegalArgumentException(
                    "kohaku.qq.shard-index must be in [0, " + shardTotal + "), was " + shardIndex);
        }
    }

    /** 由 {@link #intents()} 合并出的位掩码，Identify 时上报给网关。 */
    public int intentsMask() {
        return QqIntent.mask(intents);
    }
}
