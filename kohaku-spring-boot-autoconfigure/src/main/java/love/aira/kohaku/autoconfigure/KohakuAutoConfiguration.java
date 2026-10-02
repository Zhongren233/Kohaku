package love.aira.kohaku.autoconfigure;

import love.aira.kohaku.gateway.QqGatewayClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

/**
 * QQ 机器人自动配置入口：引入 starter 并配置 {@code kohaku.qq.app-id/app-secret} 后即自动连接网关，
 * 并暴露消息、富媒体、频道私信接口的 Bean。
 *
 * <ul>
 *   <li>{@code kohaku.qq.enabled=false} 可整体关闭（默认开启）</li>
 *   <li>{@code kohaku.qq.auto-start=false} 只装配 Bean，不随容器启动网关连接</li>
 *   <li>{@code kohaku.qq.sandbox=true} 切换到沙箱环境</li>
 *   <li>所有 Bean 都带 {@code @ConditionalOnMissingBean}，使用者可用同类型 Bean 覆写</li>
 *   <li>核心逻辑在纯 Java 的 {@code kohaku-client} 模块，本模块只做装配：属性绑定、生命周期适配、事件转发</li>
 * </ul>
 *
 * <p>装配按关注点拆成四块，便于单独阅读与覆写：
 * <ol>
 *   <li>{@link KohakuHttpApiConfiguration}：HttpClient、AccessToken 与各 REST 接口；</li>
 *   <li>{@link KohakuFeatureConfiguration}：被动回复、按钮线程池与互动路由；</li>
 *   <li>{@link KohakuDispatchConfiguration}：事件处理链与顺序；</li>
 *   <li>{@link KohakuGatewayConfiguration}：网关长连接与容器生命周期。</li>
 * </ol>
 *
 * <p>事件通过 {@code ApplicationEventPublisher} 发布：监听 {@code BotReadyEvent}、{@code BotResumedEvent}、
 * {@code BotDispatchEvent}（以及公共父类 {@code BotEvent}）即可。
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass({QqGatewayClient.class, ObjectMapper.class})
@ConditionalOnProperty(prefix = "kohaku.qq", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QqBotProperties.class)
@Import({KohakuHttpApiConfiguration.class, KohakuFeatureConfiguration.class, KohakuDispatchConfiguration.class,
        KohakuGatewayConfiguration.class})
public class KohakuAutoConfiguration {
}
