package love.aira.kohaku.autoconfigure;

import java.net.http.HttpClient;
import java.time.Duration;
import love.aira.kohaku.api.AccessTokenProvider;
import love.aira.kohaku.api.QqChannelMessageApi;
import love.aira.kohaku.api.QqGatewayApi;
import love.aira.kohaku.api.QqMediaApi;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.QqOpenApiClient;
import love.aira.kohaku.config.QqBotProperties;
import love.aira.kohaku.gateway.QqGatewayClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * QQ 机器人自动配置：引入 starter 并配置 {@code kohaku.qq.app-id/app-secret} 后即自动连接网关，
 * 并暴露消息、富媒体、频道私信接口的 Bean。
 *
 * <ul>
 *   <li>{@code kohaku.qq.enabled=false} 可整体关闭（默认开启）</li>
 *   <li>{@code kohaku.qq.auto-start=false} 只装配 Bean，不随容器启动网关连接</li>
 *   <li>{@code kohaku.qq.sandbox=true} 切换到沙箱环境</li>
 *   <li>所有 Bean 都带 {@link ConditionalOnMissingBean}，使用者可用同类型 Bean 覆写</li>
 * </ul>
 *
 * <p>事件通过 {@link ApplicationEventPublisher} 发布：监听 {@code BotReadyEvent}、{@code BotResumedEvent}、
 * {@code BotDispatchEvent}（以及公共父类 {@code BotEvent}）即可。
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass({QqGatewayClient.class, ObjectMapper.class})
@ConditionalOnProperty(prefix = "kohaku.qq", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QqBotProperties.class)
public class KohakuAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    HttpClient qqHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    AccessTokenProvider qqAccessTokenProvider(HttpClient qqHttpClient, ObjectMapper objectMapper,
                                              QqBotProperties properties) {
        return new AccessTokenProvider(qqHttpClient, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    QqOpenApiClient qqOpenApiClient(HttpClient qqHttpClient, ObjectMapper objectMapper, QqBotProperties properties,
                                    AccessTokenProvider accessTokenProvider) {
        return new QqOpenApiClient(qqHttpClient, objectMapper, properties, accessTokenProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    QqGatewayApi qqGatewayApi(QqOpenApiClient qqOpenApiClient, QqBotProperties properties) {
        return new QqGatewayApi(qqOpenApiClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    QqMessageApi qqMessageApi(QqOpenApiClient qqOpenApiClient) {
        return new QqMessageApi(qqOpenApiClient);
    }

    @Bean
    @ConditionalOnMissingBean
    QqMediaApi qqMediaApi(QqOpenApiClient qqOpenApiClient, HttpClient qqHttpClient) {
        return new QqMediaApi(qqOpenApiClient, qqHttpClient);
    }

    @Bean
    @ConditionalOnMissingBean
    QqChannelMessageApi qqChannelMessageApi(QqOpenApiClient qqOpenApiClient) {
        return new QqChannelMessageApi(qqOpenApiClient);
    }

    @Bean
    @ConditionalOnMissingBean
    QqGatewayClient qqGatewayClient(QqBotProperties properties, QqGatewayApi qqGatewayApi,
                                    AccessTokenProvider accessTokenProvider, ObjectMapper objectMapper,
                                    ApplicationEventPublisher eventPublisher) {
        return new QqGatewayClient(properties, qqGatewayApi, accessTokenProvider, objectMapper, eventPublisher);
    }
}
