package love.aira.kohaku.autoconfigure;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import love.aira.kohaku.api.AccessTokenProvider;
import love.aira.kohaku.api.QqChannelMessageApi;
import love.aira.kohaku.api.QqGatewayApi;
import love.aira.kohaku.api.QqInteractionApi;
import love.aira.kohaku.api.QqMediaApi;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.QqOpenApiClient;
import love.aira.kohaku.config.QqBotProperties;
import love.aira.kohaku.config.QqIntent;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.feature.InteractionAckMode;
import love.aira.kohaku.feature.InteractionRouter;
import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.gateway.QqGatewayClient;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.EventDispatcher;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
 *   <li>核心逻辑在纯 Java 的 {@code kohaku-client} 模块，本类只做装配：属性绑定、生命周期适配、事件转发</li>
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

    /** 统一的被动回复入口：按入站事件自动选目标、补 msg_id/msg_seq 或 event_id。 */
    @Bean
    @ConditionalOnMissingBean
    BotReplies qqBotReplies(QqMessageApi qqMessageApi, QqChannelMessageApi qqChannelMessageApi) {
        return new BotReplies(qqMessageApi, qqChannelMessageApi);
    }

    @Bean
    @ConditionalOnMissingBean
    QqInteractionApi qqInteractionApi(QqOpenApiClient qqOpenApiClient) {
        return new QqInteractionApi(qqOpenApiClient);
    }

    /**
     * 互动路由：把按钮点击投递给产生该按钮的功能（{@link BotFeature}），并调用
     * {@code PUT /interactions/{id}} 应答（平台要求 type=11/12 必须回应，否则客户端 loading 到超时）。
     * 它本身是普通 {@link BotEventHandler}，因此可用 {@code kohaku.qq.handler-order} 调整它在链中的位置。
     *
     * <p>注册了按钮回调却没有订阅 {@code INTERACTION(1<<26)} 意图时直接启动失败：否则平台不会下发
     * 按钮点击事件，客户端只会一直等待并提示"请求超时"，极难排查。
     */
    /** 按钮处理器线程池：应答在网关线程完成后，耗时逻辑在这里执行，避免顶住读循环。 */
    @Bean(name = "kohakuHandlerExecutor", destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "kohakuHandlerExecutor")
    ExecutorService kohakuHandlerExecutor(QqBotProperties properties) {
        return Executors.newFixedThreadPool(properties.handlerThreads(), runnable -> {
            Thread thread = new Thread(runnable, "kohaku-handler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnMissingBean
    InteractionRouter qqInteractionRouter(List<BotFeature> features, QqBotProperties properties,
                                          QqInteractionApi qqInteractionApi,
                                          @Qualifier("kohakuHandlerExecutor") ExecutorService handlerExecutor) {
        long buttonHandlers = features.stream().mapToLong(feature -> feature.buttonHandlers().size()).sum();
        if (buttonHandlers > 0 && (properties.toConfig().intentsMask() & QqIntent.INTERACTION.bit()) == 0) {
            throw new IllegalStateException("检测到 " + buttonHandlers + " 个按钮回调（BotFeature.buttonHandlers），"
                    + "但 kohaku.qq.intents 未包含 INTERACTION（1<<26）：按钮点击事件不会被下发，"
                    + "客户端会一直等待并提示超时。请在 kohaku.qq.intents 中加入 INTERACTION（需在开放平台申请该权限）");
        }
        return new InteractionRouter(features, qqInteractionApi::respond, InteractionAckMode.IMMEDIATE,
                handlerExecutor);
    }

    /**
     * 事件处理链：先按 {@code kohaku.qq.handler-order} 声明的 Bean 名称顺序，其余按 {@code @Order}/{@code Ordered}，
     * 最后追加各功能自带的入口处理器；处理器返回 CONSUMED 即终止，全部 IGNORED 时事件落到兜底消费者 ——
     * 发布为容器事件供 {@code @EventListener} 使用。
     */
    @Bean
    @ConditionalOnMissingBean
    EventDispatcher qqEventDispatcher(List<BotEventHandler<?>> handlers, List<BotFeature> features,
                                      QqBotProperties properties, ApplicationEventPublisher eventPublisher,
                                      ListableBeanFactory beanFactory) {
        List<BotEventHandler<?>> ordered = orderHandlers(handlers, properties.handlerOrder(), beanFactory);
        List<BotEventHandler<?>> withFeatureEntries = new ArrayList<>(ordered);
        features.forEach(feature -> withFeatureEntries.addAll(feature.messageHandlers()));
        return new EventDispatcher(withFeatureEntries, eventPublisher::publishEvent);
    }

    private static List<BotEventHandler<?>> orderHandlers(List<BotEventHandler<?>> handlers, List<String> declaredOrder,
                                                         ListableBeanFactory beanFactory) {
        if (declaredOrder.isEmpty()) {
            return handlers;   // Spring 注入集合时已按 @Order/Ordered 排序
        }
        List<BotEventHandler<?>> ordered = new ArrayList<>();
        Set<BotEventHandler<?>> remaining = new LinkedHashSet<>(handlers);
        for (String name : declaredOrder) {
            if (!beanFactory.containsBean(name)) {
                throw new IllegalStateException("kohaku.qq.handler-order 中的 Bean 不存在: " + name);
            }
            Object bean = beanFactory.getBean(name);
            if (!(bean instanceof BotEventHandler<?> handler)) {
                throw new IllegalStateException("kohaku.qq.handler-order 中的 Bean 不是 BotEventHandler: " + name);
            }
            if (remaining.remove(handler)) {
                ordered.add(handler);
            }
        }
        ordered.addAll(remaining);
        return ordered;
    }

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
