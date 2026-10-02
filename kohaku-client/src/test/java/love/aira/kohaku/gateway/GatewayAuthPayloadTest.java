package love.aira.kohaku.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import love.aira.kohaku.config.KohakuConfig;
import love.aira.kohaku.config.QqIntent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Identify / Resume 报文结构与日志脱敏。 */
class GatewayAuthPayloadTest {

    private final JsonMapper mapper = new JsonMapper();
    private final KohakuConfig config = new KohakuConfig("app-id", "app-secret", "https://api.example", "https://api.example/token",
            List.of(QqIntent.PUBLIC_GUILD_MESSAGES, QqIntent.INTERACTION), 0, 1, "kohaku-client-name", true,
            Duration.ofSeconds(1), Duration.ofSeconds(60), false);

    @Test
    void identifyCarriesIntentsShardAndClientProperties() {
        JsonNode payload = GatewayAuthPayload.identify(mapper, config, "TOKEN_VALUE");

        assertThat(payload.path("op").asInt()).isEqualTo(GatewayOp.IDENTIFY);
        JsonNode data = payload.path("d");
        assertThat(data.path("token").stringValue()).isEqualTo("TOKEN_VALUE");
        assertThat(data.path("intents").asLong()).isEqualTo(config.intentsMask());
        assertThat(data.path("shard").get(0).asInt()).isZero();
        assertThat(data.path("shard").get(1).asInt()).isEqualTo(1);
        assertThat(data.path("properties").path("$browser").stringValue()).isEqualTo("kohaku-client-name");
        assertThat(data.path("properties").path("$device").stringValue()).isEqualTo("kohaku-client-name");
        assertThat(data.has("session_id")).isFalse();
        assertThat(data.has("seq")).isFalse();
    }

    @Test
    void resumeCarriesSessionAndSeqInsteadOfIntents() {
        JsonNode payload = GatewayAuthPayload.resume(mapper, "TOKEN_VALUE", "SESSION_ID", 42L);

        assertThat(payload.path("op").asInt()).isEqualTo(GatewayOp.RESUME);
        JsonNode data = payload.path("d");
        assertThat(data.path("token").stringValue()).isEqualTo("TOKEN_VALUE");
        assertThat(data.path("session_id").stringValue()).isEqualTo("SESSION_ID");
        assertThat(data.path("seq").asLong()).isEqualTo(42L);
        assertThat(data.has("intents")).isFalse();
        assertThat(data.has("shard")).isFalse();
    }

    @Test
    void redactsTokenButKeepsEverythingElse() {
        String identify = mapper.writeValueAsString(GatewayAuthPayload.identify(mapper, config, "SECRET_TOKEN"));

        String redacted = GatewayAuthPayload.redact(identify);

        assertThat(redacted).doesNotContain("SECRET_TOKEN").contains("\"token\":\"***\"");
        assertThat(mapper.readTree(redacted).path("d").path("shard").get(1).asInt()).isEqualTo(1);
    }
}
