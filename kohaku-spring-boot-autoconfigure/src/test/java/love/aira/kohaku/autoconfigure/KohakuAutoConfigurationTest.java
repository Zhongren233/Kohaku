package love.aira.kohaku.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.http.HttpClient;
import love.aira.kohaku.api.AccessTokenProvider;
import love.aira.kohaku.api.QqChannelMessageApi;
import love.aira.kohaku.api.QqGatewayApi;
import love.aira.kohaku.api.QqMediaApi;
import love.aira.kohaku.api.QqInteractionApi;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.QqOpenApiClient;
import love.aira.kohaku.config.KohakuConfig;
import love.aira.kohaku.gateway.QqGatewayClient;
import love.aira.kohaku.gateway.handler.EventDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/** 自动装配的条件、覆写能力与快速失败行为。 */
class KohakuAutoConfigurationTest {

    /**
     * 模拟「组件扫描根覆盖 love.aira.kohaku.autoconfigure」的应用（如 kohaku-example，
     * 其 {@code @SpringBootApplication} 在包 {@code love.aira.kohaku}）：本模块的 @Bean 配置类
     * 必须对组件扫描不可见，否则 enabled=false 会被绕过。
     *
     * <p>排除过滤器与 {@code @SpringBootApplication} 一致（{@link AutoConfigurationExcludeFilter}）；
     * 另排除本测试包内的测试类，避免它们混入扫描结果。
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "love.aira.kohaku.autoconfigure",
            excludeFilters = {
                    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                    @ComponentScan.Filter(type = FilterType.REGEX,
                            pattern = "love\\.aira\\.kohaku\\.autoconfigure\\..*Test.*")
            })
    static class ScanningRoot {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, KohakuAutoConfiguration.class))
            .withPropertyValues("kohaku.qq.app-id=test-app", "kohaku.qq.app-secret=test-secret",
                    "kohaku.qq.auto-start=false");

    @Test
    void registersEveryBeanByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(QqBotProperties.class);
            assertThat(context).hasSingleBean(HttpClient.class);
            assertThat(context).hasSingleBean(AccessTokenProvider.class);
            assertThat(context).hasSingleBean(QqOpenApiClient.class);
            assertThat(context).hasSingleBean(QqGatewayApi.class);
            assertThat(context).hasSingleBean(QqInteractionApi.class);
            assertThat(context).hasSingleBean(QqMessageApi.class);
            assertThat(context).hasSingleBean(QqMediaApi.class);
            assertThat(context).hasSingleBean(QqChannelMessageApi.class);
            assertThat(context).hasSingleBean(QqGatewayClient.class);
            assertThat(context).hasSingleBean(KohakuLifecycle.class);
            assertThat(context).hasSingleBean(EventDispatcher.class);
        });
    }

    @Test
    void disabledByProperty() {
        runner.withPropertyValues("kohaku.qq.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(QqBotProperties.class);
            assertThat(context).doesNotHaveBean(QqMessageApi.class);
            assertThat(context).doesNotHaveBean(QqGatewayClient.class);
        });
    }

    /**
     * enabled=false 必须对「扫描根覆盖 autoconfigure 包」的应用同样生效：
     * 四个 @Bean 配置类若被组件扫描注册，会在 QqBotProperties（由被跳过的入口提供）缺失时启动失败。
     */
    @Test
    void componentScanCannotBypassEnabledFalse() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, KohakuAutoConfiguration.class))
                .withUserConfiguration(ScanningRoot.class)
                .withPropertyValues("kohaku.qq.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(QqBotProperties.class);
                    assertThat(context).doesNotHaveBean(QqMessageApi.class);
                });
    }

    /** enabled=false 时不校验凭据：允许使用者在共享配置里默认关闭本 starter。 */
    @Test
    void disabledWithoutCredentialsStartsFine() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(JacksonAutoConfiguration.class, KohakuAutoConfiguration.class))
                .withPropertyValues("kohaku.qq.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsFastWhenCredentialsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(JacksonAutoConfiguration.class, KohakuAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("kohaku.qq.app-id is missing");
                });
    }

    @Test
    void sandboxSwitchesApiBaseUrl() {
        runner.withPropertyValues("kohaku.qq.sandbox=true").run(context -> {
            QqBotProperties properties = context.getBean(QqBotProperties.class);
            assertThat(properties.sandbox()).isTrue();
            assertThat(properties.toConfig().effectiveApiBaseUrl()).isEqualTo(KohakuConfig.SANDBOX_API_BASE_URL);
        });
    }

    @Test
    void userProvidedBeanWins() {
        QqMessageApi custom = mock(QqMessageApi.class);
        runner.withBean(QqMessageApi.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(QqMessageApi.class);
            assertThat(context.getBean(QqMessageApi.class)).isSameAs(custom);
        });
    }

    @Test
    void userProvidedHttpClientIsReused() {
        HttpClient custom = HttpClient.newHttpClient();
        runner.withBean(HttpClient.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(HttpClient.class);
            assertThat(context.getBean(HttpClient.class)).isSameAs(custom);
        });
    }
}
