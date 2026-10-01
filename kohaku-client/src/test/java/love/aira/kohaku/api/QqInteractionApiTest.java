package love.aira.kohaku.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import love.aira.kohaku.config.KohakuConfig;
import love.aira.kohaku.config.QqIntent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class QqInteractionApiTest {

    private static final String INTERACTION_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    private FakeQqApiServer server;
    private QqInteractionApi api;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        server = new FakeQqApiServer();
        mapper = new JsonMapper();
        KohakuConfig config = new KohakuConfig("app-id", "app-secret", server.baseUrl(),
                server.baseUrl() + "/app/getAppAccessToken", List.of(QqIntent.INTERACTION), 0, 1, "kohaku", true,
                Duration.ofSeconds(1), Duration.ofSeconds(60), false);
        server.stub("POST /app/getAppAccessToken", 200,
                "{\"access_token\":\"test-token\",\"expires_in\":\"7200\"}");
        HttpClient httpClient = HttpClient.newHttpClient();
        api = new QqInteractionApi(new QqOpenApiClient(httpClient, mapper, config,
                new AccessTokenProvider(httpClient, mapper, config)));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void respondsWithPutAndCode() {
        server.stub("PUT /interactions/" + INTERACTION_ID, 200, "{}");

        api.respond(INTERACTION_ID, QqInteractionApi.CODE_SUCCESS);

        FakeQqApiServer.Call call = server.calls("PUT /interactions/" + INTERACTION_ID).getFirst();
        assertThat(call.authorization()).isEqualTo("QQBot test-token");
        assertThat(mapper.readTree(call.body())).isEqualTo(mapper.readTree("{\"code\":0}"));
    }

    @Test
    void mapsPlatformErrorToException() {
        server.stub("PUT /interactions/" + INTERACTION_ID, 400,
                "{\"code\":630003,\"message\":\"appid invalid\"}");

        assertThatThrownBy(() -> api.respond(INTERACTION_ID, QqInteractionApi.CODE_FAILED))
                .isInstanceOf(QqApiException.class)
                .hasMessageContaining("630003")
                .satisfies(exception -> assertThat(((QqApiException) exception).code()).isEqualTo(630003));
    }

    @Test
    void rejectsBlankInteractionId() {
        assertThatThrownBy(() -> api.respond(" ", QqInteractionApi.CODE_SUCCESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interaction_id");
    }
}
