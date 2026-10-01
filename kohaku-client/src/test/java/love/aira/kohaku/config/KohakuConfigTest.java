package love.aira.kohaku.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 核心配置（纯 Java，无 Spring）的行为：校验、位掩码、沙箱地址与便捷覆写。 */
class KohakuConfigTest {

    private static final List<QqIntent> INTENTS = List.of(QqIntent.GUILDS, QqIntent.PUBLIC_GUILD_MESSAGES);

    @Test
    void ofBuildsUsableDefaults() {
        KohakuConfig config = KohakuConfig.of("app", "secret", INTENTS);

        assertThat(config.effectiveApiBaseUrl()).isEqualTo(KohakuConfig.DEFAULT_API_BASE_URL);
        assertThat(config.tokenUrl()).isEqualTo(KohakuConfig.DEFAULT_TOKEN_URL);
        assertThat(config.intentsMask()).isEqualTo((1 << 0) | (1 << 30));
        assertThat(config.shardIndex()).isZero();
        assertThat(config.shardTotal()).isEqualTo(1);
        assertThat(config.autoStart()).isTrue();
        assertThat(config.reconnectInitialDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(config.reconnectMaxDelay()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void sandboxOverridesApiBaseUrl() {
        assertThat(KohakuConfig.of("app", "secret", INTENTS).withSandbox(true).effectiveApiBaseUrl())
                .isEqualTo(KohakuConfig.SANDBOX_API_BASE_URL);
    }

    @Test
    void withAutoStartKeepsOtherFields() {
        KohakuConfig config = KohakuConfig.of("app", "secret", INTENTS);

        assertThat(config.withAutoStart(false)).satisfies(updated -> {
            assertThat(updated.autoStart()).isFalse();
            assertThat(updated.appId()).isEqualTo(config.appId());
            assertThat(updated.intentsMask()).isEqualTo(config.intentsMask());
            assertThat(updated.effectiveApiBaseUrl()).isEqualTo(config.effectiveApiBaseUrl());
        });
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> KohakuConfig.of(" ", "secret", INTENTS))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("appId");
        assertThatThrownBy(() -> KohakuConfig.of("app", null, INTENTS))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("appSecret");
        assertThatThrownBy(() -> KohakuConfig.of("app", "secret", List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("intents");
        assertThatThrownBy(() -> new KohakuConfig("app", "secret", KohakuConfig.DEFAULT_API_BASE_URL,
                KohakuConfig.DEFAULT_TOKEN_URL, INTENTS, 2, 2, "kohaku", true, Duration.ofSeconds(1),
                Duration.ofSeconds(60), false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("shardIndex");
    }

    @Test
    void intentsAreDefensivelyCopied() {
        List<QqIntent> mutable = new java.util.ArrayList<>(INTENTS);
        KohakuConfig config = KohakuConfig.of("app", "secret", mutable);

        mutable.clear();

        assertThat(config.intents()).hasSize(2);
        assertThat(config.intentsMask()).isEqualTo((1 << 0) | (1 << 30));
    }
}
