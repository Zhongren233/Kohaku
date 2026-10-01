package love.aira.kohaku.config;

import java.time.Duration;
import java.util.List;
import love.aira.kohaku.config.KohakuConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * QQ 机器人开放平台配置（{@code kohaku.qq.*}）。
 *
 * @param appId                  机器人 AppID
 * @param appSecret              机器人 AppSecret，用于换取 AccessToken
 * @param apiBaseUrl             开放平台 API 基地址（沙箱见 {@link #sandbox()}）
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
 * @param enabled                是否启用本 starter（置 false 可整体关闭，连凭据校验也跳过）
 * @param sandbox                是否为沙箱环境；为 true 时强制使用 {@link KohakuConfig#SANDBOX_API_BASE_URL}
 * @param handlerThreads         按钮处理器的工作线程数（默认 1，保持顺序）；耗时逻辑在此线程池执行，
 *                               不占用网关读循环
 * @param handlerOrder           事件处理器（{@code BotEventHandler} Bean）的执行顺序，按 Bean 名称声明；
 *                               未列出的处理器排在其后，按 {@code @Order}/{@code Ordered} 顺序执行
 */
@ConfigurationProperties(prefix = "kohaku.qq")
public record QqBotProperties(
        String appId,
        String appSecret,
        @DefaultValue("https://api.bot.qq.com") String apiBaseUrl,
        @DefaultValue("https://api.bot.qq.com/app/getAppAccessToken") String tokenUrl,
        @DefaultValue("PUBLIC_GUILD_MESSAGES") List<QqIntent> intents,
        @DefaultValue("0") int shardIndex,
        @DefaultValue("1") int shardTotal,
        @DefaultValue("kohaku") String clientName,
        @DefaultValue("true") boolean autoStart,
        @DefaultValue("1s") Duration reconnectInitialDelay,
        @DefaultValue("60s") Duration reconnectMaxDelay,
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean sandbox,
        List<String> handlerOrder,
        @DefaultValue("1") int handlerThreads) {

    /**
     * 缺凭据时的补充提示：{@code spring.config.import} 的 file: 相对路径按进程工作目录解析，
     * 带上当前目录便于定位「配置没被读到」这类问题。
     */
    private static final String MISSING_HINT = "（当前工作目录 "
            + System.getProperty("user.dir") + "，spring.config.import 中 file: 的相对路径即以此为基准）";

    public QqBotProperties {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("kohaku.qq.app-id is missing: 在配置文件中填写，或设置环境变量 "
                    + "KOHAKU_QQ_APPID" + MISSING_HINT);
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("kohaku.qq.app-secret is missing: 在配置文件中填写，或设置环境变量 "
                    + "KOHAKU_QQ_APPSECRET" + MISSING_HINT);
        }
        if (intents == null || intents.isEmpty()) {
            throw new IllegalArgumentException(
                    "kohaku.qq.intents must not be empty, e.g. [PUBLIC_GUILD_MESSAGES] or [GUILDS, PUBLIC_GUILD_MESSAGES]");
        }
        intents = List.copyOf(intents);
        handlerOrder = handlerOrder == null ? List.of() : List.copyOf(handlerOrder);
        if (handlerThreads < 1) {
            throw new IllegalArgumentException("kohaku.qq.handler-threads must be >= 1, was " + handlerThreads);
        }
        if (shardTotal < 1) {
            throw new IllegalArgumentException("kohaku.qq.shard-total must be >= 1, was " + shardTotal);
        }
        if (shardIndex < 0 || shardIndex >= shardTotal) {
            throw new IllegalArgumentException(
                    "kohaku.qq.shard-index must be in [0, " + shardTotal + "), was " + shardIndex);
        }
    }

    /** 转换为框架无关的运行时配置（校验在两边都保留：这里给出带属性名的提示，核心侧保证独立可用）。 */
    public KohakuConfig toConfig() {
        return new KohakuConfig(appId, appSecret, apiBaseUrl, tokenUrl, intents, shardIndex, shardTotal, clientName,
                autoStart, reconnectInitialDelay, reconnectMaxDelay, sandbox);
    }
}
