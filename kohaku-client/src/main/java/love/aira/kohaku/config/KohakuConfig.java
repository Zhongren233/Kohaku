package love.aira.kohaku.config;

import java.time.Duration;
import java.util.List;

/**
 * 运行时配置（纯 Java，不依赖 Spring）：非 Spring 宿主可直接构造，Spring 侧由
 * {@code kohaku.qq.*} 绑定后转换而来。
 *
 * @param appId                  机器人 AppID
 * @param appSecret              机器人 AppSecret，用于换取 AccessToken
 * @param apiBaseUrl             开放平台 API 基地址（{@link #sandbox()} 为真时忽略）
 * @param tokenUrl               AccessToken 获取地址
 * @param intents                事件订阅，多选 {@link QqIntent}（位掩码由 {@link #intentsMask()} 合成）
 * @param shardIndex             分片序号，从 0 开始
 * @param shardTotal             分片总数，单连接为 1
 * @param clientName             上报给网关的 $browser/$device 标识
 * @param autoStart              是否随宿主启动即连接
 * @param reconnectInitialDelay  重连退避起始延迟
 * @param reconnectMaxDelay      重连退避上限
 * @param sandbox                是否为沙箱环境
 */
public record KohakuConfig(
        String appId,
        String appSecret,
        String apiBaseUrl,
        String tokenUrl,
        List<QqIntent> intents,
        int shardIndex,
        int shardTotal,
        String clientName,
        boolean autoStart,
        Duration reconnectInitialDelay,
        Duration reconnectMaxDelay,
        boolean sandbox) {

    /** 正式环境开放平台地址：官方文档「API 调用指南」的统一请求地址。 */
    public static final String DEFAULT_API_BASE_URL = "https://api.bot.qq.com";
    /** 沙箱环境开放平台地址（v2 文档未单列，按统一域名推断；实测可解析并返回同一套错误码）。 */
    public static final String SANDBOX_API_BASE_URL = "https://sandbox.api.bot.qq.com";
    /** AccessToken 获取地址：官方文档「获取访问凭证」。（旧域名 https://bots.qq.com/... 目前仍可用） */
    public static final String DEFAULT_TOKEN_URL = "https://api.bot.qq.com/app/getAppAccessToken";

    /** 常用默认值：只需凭据与订阅的事件。 */
    public static KohakuConfig of(String appId, String appSecret, List<QqIntent> intents) {
        return new KohakuConfig(appId, appSecret, DEFAULT_API_BASE_URL, DEFAULT_TOKEN_URL, intents, 0, 1, "kohaku",
                true, Duration.ofSeconds(1), Duration.ofSeconds(60), false);
    }

    public KohakuConfig {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("appSecret must not be blank");
        }
        if (intents == null || intents.isEmpty()) {
            throw new IllegalArgumentException("intents must not be empty");
        }
        intents = List.copyOf(intents);
        if (shardTotal < 1) {
            throw new IllegalArgumentException("shardTotal must be >= 1, was " + shardTotal);
        }
        if (shardIndex < 0 || shardIndex >= shardTotal) {
            throw new IllegalArgumentException("shardIndex must be in [0, " + shardTotal + "), was " + shardIndex);
        }
    }

    /** 由 {@link #intents()} 合成的位掩码，Identify 时上报。 */
    public int intentsMask() {
        return QqIntent.mask(intents);
    }

    /** 实际使用的开放平台地址：沙箱开关优先。 */
    public String effectiveApiBaseUrl() {
        return sandbox ? SANDBOX_API_BASE_URL : apiBaseUrl;
    }

    public KohakuConfig withAutoStart(boolean autoStart) {
        return new KohakuConfig(appId, appSecret, apiBaseUrl, tokenUrl, intents, shardIndex, shardTotal, clientName,
                autoStart, reconnectInitialDelay, reconnectMaxDelay, sandbox);
    }

    public KohakuConfig withSandbox(boolean sandbox) {
        return new KohakuConfig(appId, appSecret, apiBaseUrl, tokenUrl, intents, shardIndex, shardTotal, clientName,
                autoStart, reconnectInitialDelay, reconnectMaxDelay, sandbox);
    }
}
