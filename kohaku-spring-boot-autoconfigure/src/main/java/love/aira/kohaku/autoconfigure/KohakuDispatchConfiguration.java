package love.aira.kohaku.autoconfigure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.EventDispatcher;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/** 事件处理链装配：按配置排序 BotEventHandler，并把各功能的消息入口追加到链尾。 */
@AutoConfiguration
public class KohakuDispatchConfiguration {

    /**
     * 事件处理链：先按 {@code kohaku.qq.handler-order} 声明的 Bean 名称顺序，其余按 {@code @Order}/{@code Ordered}，
     * 最后追加各功能自带的入口处理器；处理器返回 CONSUMED 只终止链内后续处理器。
     * 无论事件是否被消费，都会作为容器事件发布，供 {@code @EventListener} 观察（观察者通道）。
     */
    @Bean
    @ConditionalOnMissingBean
    EventDispatcher qqEventDispatcher(List<BotEventHandler<?>> handlers, List<BotFeature> features,
                                      QqBotProperties properties, ApplicationEventPublisher eventPublisher,
                                      ListableBeanFactory beanFactory,
                                      @Qualifier("kohakuHandlerExecutor") ExecutorService handlerExecutor) {
        List<BotEventHandler<?>> ordered = orderHandlers(handlers, properties.handlerOrder(), beanFactory);
        List<BotEventHandler<?>> withFeatureEntries = new ArrayList<>(ordered);
        features.forEach(feature -> withFeatureEntries.addAll(feature.messageHandlers()));
        return new EventDispatcher(withFeatureEntries, eventPublisher::publishEvent, handlerExecutor);
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
}
