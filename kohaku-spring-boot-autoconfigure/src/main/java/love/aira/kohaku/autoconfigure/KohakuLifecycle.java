package love.aira.kohaku.autoconfigure;

import love.aira.kohaku.gateway.QqGatewayClient;
import org.springframework.context.SmartLifecycle;

/**
 * 把纯 Java 的 {@link QqGatewayClient} 接入 Spring 容器生命周期：{@code kohaku.qq.auto-start=true}（默认）
 * 时随容器启动连接、容器关闭时优雅断开。
 */
public class KohakuLifecycle implements SmartLifecycle {

    private final QqGatewayClient client;
    private final QqBotProperties properties;

    public KohakuLifecycle(QqGatewayClient client, QqBotProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void start() {
        client.start();
    }

    @Override
    public void stop() {
        client.stop();
    }

    @Override
    public boolean isRunning() {
        return client.isRunning();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.autoStart();
    }

    /** 最后启动、最先停止：容器关闭时先断开网关，再关闭其他组件。 */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
