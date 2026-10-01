package love.aira.kohaku.config;

import static org.assertj.core.api.Assertions.assertThat;

import love.aira.kohaku.autoconfigure.KohakuAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class QqBotPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, KohakuAutoConfiguration.class))
            .withPropertyValues("kohaku.qq.app-id=test-app", "kohaku.qq.app-secret=test-secret",
                    "kohaku.qq.auto-start=false");

    @Test
    void defaultsToPublicGuildMessages() {
        runner.run(context -> {
            QqBotProperties properties = context.getBean(QqBotProperties.class);
            assertThat(properties.intents()).containsExactly(QqIntent.PUBLIC_GUILD_MESSAGES);
            assertThat(properties.intentsMask()).isEqualTo(1 << 30);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.sandbox()).isFalse();
            assertThat(properties.autoStart()).isFalse();
        });
    }

    @Test
    void bindsYamlListOfIntents() {
        runner.withPropertyValues(
                        "kohaku.qq.intents[0]=GUILDS",
                        "kohaku.qq.intents[1]=PUBLIC_GUILD_MESSAGES",
                        "kohaku.qq.intents[2]=GROUP_AND_C2C_EVENT")
                .run(context -> {
                    QqBotProperties properties = context.getBean(QqBotProperties.class);
                    assertThat(properties.intents()).containsExactly(
                            QqIntent.GUILDS, QqIntent.PUBLIC_GUILD_MESSAGES, QqIntent.GROUP_AND_C2C_EVENT);
                    assertThat(properties.intentsMask()).isEqualTo((1 << 0) | (1 << 30) | (1 << 25));
                });
    }

    @Test
    void bindsCommaSeparatedIntents() {
        runner.withPropertyValues("kohaku.qq.intents=GUILDS,PUBLIC_GUILD_MESSAGES")
                .run(context -> {
                    QqBotProperties properties = context.getBean(QqBotProperties.class);
                    assertThat(properties.intents()).containsExactly(QqIntent.GUILDS, QqIntent.PUBLIC_GUILD_MESSAGES);
                    assertThat(properties.intentsMask()).isEqualTo((1 << 0) | (1 << 30));
                });
    }

    @Test
    void rejectsUnknownIntentName() {
        runner.withPropertyValues("kohaku.qq.intents=NOT_AN_INTENT")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsEmptyIntents() {
        runner.withPropertyValues("kohaku.qq.intents=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("kohaku.qq.intents must not be empty");
                });
    }

    @Test
    void rejectsOutOfRangeShardIndex() {
        runner.withPropertyValues("kohaku.qq.shard-total=2", "kohaku.qq.shard-index=2")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("kohaku.qq.shard-index");
                });
    }
}
