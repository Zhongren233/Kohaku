package love.aira.kohaku.autoconfigure;

import love.aira.kohaku.api.AccessTokenProvider;
import love.aira.kohaku.api.QqGatewayApi;
import love.aira.kohaku.gateway.QqGatewayClient;
import love.aira.kohaku.gateway.handler.EventDispatcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/** 网关装配：WebSocket 长连接客户端与容器生命周期适配。 */
@AutoConfiguration
public class KohakuGatewayConfiguration {

    @Bean
    @ConditionalOnMissingBean
    QqGatewayClient qqGatewayClient(QqBotProperties properties, QqGatewayApi qqGatewayApi,
                                    AccessTokenProvider accessTokenProvider, ObjectMapper objectMapper,
                                    EventDispatcher eventDispatcher) {
        return new QqGatewayClient(properties.toConfig(), qqGatewayApi, accessTokenProvider, objectMapper,
                eventDispatcher::dispatch);
    }

    @Bean
    @ConditionalOnMissingBean
    KohakuLifecycle kohakuLifecycle(QqGatewayClient qqGatewayClient, QqBotProperties properties) {
        return new KohakuLifecycle(qqGatewayClient, properties);
    }
}
