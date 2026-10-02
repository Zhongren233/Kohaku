package love.aira.kohaku.autoconfigure;

import java.net.http.HttpClient;
import java.time.Duration;
import love.aira.kohaku.api.AccessTokenProvider;
import love.aira.kohaku.api.QqChannelMessageApi;
import love.aira.kohaku.api.QqGatewayApi;
import love.aira.kohaku.api.QqInteractionApi;
import love.aira.kohaku.api.QqMediaApi;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.QqOpenApiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** QQ 开放平台 HTTP 侧装配：HttpClient、AccessToken 与各 REST 接口 Bean。 */
@Configuration(proxyBeanMethods = false)
public class KohakuHttpApiConfiguration {

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
        return new AccessTokenProvider(qqHttpClient, objectMapper, properties.toConfig());
    }

    @Bean
    @ConditionalOnMissingBean
    QqOpenApiClient qqOpenApiClient(HttpClient qqHttpClient, ObjectMapper objectMapper, QqBotProperties properties,
                                    AccessTokenProvider accessTokenProvider) {
        return new QqOpenApiClient(qqHttpClient, objectMapper, properties.toConfig(), accessTokenProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    QqGatewayApi qqGatewayApi(QqOpenApiClient qqOpenApiClient, QqBotProperties properties) {
        return new QqGatewayApi(qqOpenApiClient, properties.toConfig());
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
    QqInteractionApi qqInteractionApi(QqOpenApiClient qqOpenApiClient) {
        return new QqInteractionApi(qqOpenApiClient);
    }
}
