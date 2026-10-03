package love.aira.kohaku.autoconfigure;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import love.aira.kohaku.api.QqChannelMessageApi;
import love.aira.kohaku.api.QqInteractionApi;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.config.QqIntent;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.interaction.InteractionAckMode;
import love.aira.kohaku.interaction.InteractionRouter;
import love.aira.kohaku.reply.BotReplies;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 功能装配：被动回复入口、按钮处理器线程池与互动路由（含 INTERACTION 意图校验）。 */
@AutoConfiguration
public class KohakuFeatureConfiguration {

    /** 统一的被动回复入口：按入站事件自动选目标、补 msg_id/msg_seq 或 event_id。 */
    @Bean
    @ConditionalOnMissingBean
    BotReplies qqBotReplies(QqMessageApi qqMessageApi, QqChannelMessageApi qqChannelMessageApi) {
        return new BotReplies(qqMessageApi, qqChannelMessageApi);
    }

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

    /**
     * 互动路由：把按钮点击投递给产生该按钮的功能（{@link BotFeature}），并调用
     * {@code PUT /interactions/{id}} 应答（平台要求 type=11/12 必须回应，否则客户端 loading 到超时）。
     * 它本身是普通 {@link BotEventHandler}，因此可用 {@code kohaku.qq.handler-order} 调整它在链中的位置。
     *
     * <p>注册了按钮回调却没有订阅 {@code INTERACTION(1<<26)} 意图时直接启动失败：否则平台不会下发
     * 按钮点击事件，客户端只会一直等待并提示"请求超时"，极难排查。
     */
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
}
