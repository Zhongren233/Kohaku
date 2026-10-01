package love.aira.kohaku.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.http.HttpClient;
import love.aira.kohaku.api.AccessTokenProvider;
import love.aira.kohaku.api.QqChannelMessageApi;
import love.aira.kohaku.api.QqGatewayApi;
import love.aira.kohaku.api.QqMediaApi;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.QqOpenApiClient;
import love.aira.kohaku.config.QqBotProperties;
import love.aira.kohaku.gateway.QqGatewayClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 自动装配的条件、覆写能力与快速失败行为。 */
class KohakuAutoConfigurationTest {

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
            assertThat(context).hasSingleBean(QqMessageApi.class);
            assertThat(context).hasSingleBean(QqMediaApi.class);
            assertThat(context).hasSingleBean(QqChannelMessageApi.class);
            assertThat(context).hasSingleBean(QqGatewayClient.class);
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
            assertThat(properties.effectiveApiBaseUrl()).isEqualTo(QqBotProperties.SANDBOX_API_BASE_URL);
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
