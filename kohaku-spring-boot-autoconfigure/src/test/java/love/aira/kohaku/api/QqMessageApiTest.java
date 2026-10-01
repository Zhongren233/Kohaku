package love.aira.kohaku.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.api.model.SendMessageResponse;
import love.aira.kohaku.api.model.StreamMessageRequest;
import love.aira.kohaku.api.model.StreamMessageResponse;
import love.aira.kohaku.config.QqBotProperties;
import love.aira.kohaku.config.QqIntent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class QqMessageApiTest {

    private static final String TOKEN_BODY = "{\"access_token\":\"test-token\",\"expires_in\":\"7200\"}";

    private FakeQqApiServer server;
    private QqMessageApi api;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        server = new FakeQqApiServer();
        mapper = new JsonMapper();
        QqBotProperties properties = new QqBotProperties("app-id", "app-secret", server.baseUrl(),
                server.baseUrl() + "/app/getAppAccessToken", List.of(QqIntent.PUBLIC_GUILD_MESSAGES), 0, 1,
                "kohaku", true, Duration.ofSeconds(1), Duration.ofSeconds(60), true, false);
        server.stub("POST /app/getAppAccessToken", 200, TOKEN_BODY);
        AccessTokenProvider tokens = new AccessTokenProvider(HttpClient.newHttpClient(), mapper, properties);
        api = new QqMessageApi(new QqOpenApiClient(HttpClient.newHttpClient(), mapper, properties, tokens));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendsTextReplyToUser() {
        server.stub("POST /v2/users/OPENID_USER/messages", 200, """
                {"id":"ROBOT1.0_abc","timestamp":"2026-10-01T12:00:00+08:00",
                 "ext_info":{"ref_idx":"REFIDX_xyz"}}""");

        SendMessageResponse response = api.sendToUser("OPENID_USER",
                SendMessageRequest.text("你好").replyingTo("MSG_ID_1").withKeyboard(
                        Keyboard.of(Keyboard.Row.of(Keyboard.Button.command("b1", "重来", "/retry")))));

        assertThat(response.id()).isEqualTo("ROBOT1.0_abc");
        assertThat(response.timestamp()).isEqualTo("2026-10-01T12:00:00+08:00");
        assertThat(response.refIdx()).isEqualTo("REFIDX_xyz");

        FakeQqApiServer.Call call = server.calls("POST /v2/users/OPENID_USER/messages").getFirst();
        assertThat(call.authorization()).isEqualTo("QQBot test-token");
        JsonNode body = mapper.readTree(call.body());
        assertThat(body).isEqualTo(mapper.readTree("""
                {"msg_type":0,"content":"你好","msg_id":"MSG_ID_1","keyboard":{"content":{"rows":[
                  {"buttons":[{"id":"b1","render_data":{"label":"重来"},
                     "action":{"type":2,"permission":{"type":2},"data":"/retry"}}]}]}}}"""));
    }

    @Test
    void omitsNullFieldsAndSupportsEveryContentType() {
        server.stub("POST /v2/groups/GROUP_OPENID/messages", 200, "{\"id\":\"m1\"}");
        server.stub("POST /v2/groups/GROUP_OPENID/messages", 200, "{\"id\":\"m2\"}");
        server.stub("POST /v2/users/OPENID_USER/messages", 200, "{\"id\":\"m3\"}");

        api.sendToGroup("GROUP_OPENID", SendMessageRequest.markdown("**hi**").withMsgSeq(3));
        JsonNode markdown = mapper.readTree(
                server.calls("POST /v2/groups/GROUP_OPENID/messages").getFirst().body());
        assertThat(markdown).isEqualTo(mapper.readTree(
                "{\"msg_type\":2,\"markdown\":{\"content\":\"**hi**\"},\"msg_seq\":3}"));

        api.sendToGroup("GROUP_OPENID", SendMessageRequest.media("FILE_INFO_1"));
        JsonNode media = mapper.readTree(server.calls("POST /v2/groups/GROUP_OPENID/messages").get(1).body());
        assertThat(media).isEqualTo(mapper.readTree("{\"msg_type\":7,\"media\":{\"file_info\":\"FILE_INFO_1\"}}"));

        api.sendToUser("OPENID_USER", SendMessageRequest.typing(30));
        JsonNode typing = mapper.readTree(server.calls("POST /v2/users/OPENID_USER/messages").getFirst().body());
        assertThat(typing).isEqualTo(mapper.readTree(
                "{\"msg_type\":6,\"input_notify\":{\"input_type\":1,\"input_second\":30}}"));
    }

    @Test
    void streamsUserMessageWithStreamIdAndFinishState() {
        server.stub("POST /v2/users/OPENID_USER/stream_messages", 200,
                "{\"id\":\"STREAM_ID_1\",\"timestamp\":\"t\",\"remain_msg_len\":100}");
        server.stub("POST /v2/users/OPENID_USER/stream_messages", 200, "{\"id\":\"STREAM_ID_1\"}");
        server.stub("POST /v2/users/OPENID_USER/stream_messages", 200, "{\"id\":\"STREAM_ID_1\"}");

        StreamMessageResponse opened = api.sendStreamToUser("OPENID_USER",
                StreamMessageRequest.first("第一段", StreamMessageRequest.CONTENT_MARKDOWN).withMsgId("MSG_ID_1"));
        assertThat(opened.id()).isEqualTo("STREAM_ID_1");
        assertThat(opened.remainMsgLen()).isEqualTo(100);

        api.sendStreamToUser("OPENID_USER", StreamMessageRequest.first("第二段", StreamMessageRequest.CONTENT_MARKDOWN)
                .nextSlice(opened.id(), 1, "第二段"));
        api.sendStreamToUser("OPENID_USER", StreamMessageRequest.first("第三段", StreamMessageRequest.CONTENT_MARKDOWN)
                .finish(opened.id(), 2, "第三段"));

        List<FakeQqApiServer.Call> calls = server.calls("POST /v2/users/OPENID_USER/stream_messages");
        assertThat(mapper.readTree(calls.getFirst().body())).isEqualTo(mapper.readTree(
                "{\"input_mode\":\"append\",\"input_state\":1,\"index\":0,\"content_type\":\"markdown\","
                        + "\"content_raw\":\"第一段\",\"msg_id\":\"MSG_ID_1\"}"));
        assertThat(mapper.readTree(calls.get(2).body())).isEqualTo(mapper.readTree(
                "{\"input_mode\":\"append\",\"input_state\":10,\"index\":2,\"content_type\":\"markdown\","
                        + "\"content_raw\":\"第三段\",\"stream_msg_id\":\"STREAM_ID_1\"}"));
    }

    @Test
    void recallsUserAndGroupMessagesWithEncodedPathSegments() {
        server.stub("DELETE /v2/users/OPENID_USER/messages/MSG%2F1", 200, "{}");
        server.stub("DELETE /v2/groups/GROUP_OPENID/messages/MSG%202", 200, "{}");

        api.recallFromUser("OPENID_USER", "MSG/1");
        api.recallFromGroup("GROUP_OPENID", "MSG 2");

        assertThat(server.calls("DELETE /v2/users/OPENID_USER/messages/MSG%2F1")).hasSize(1);
        assertThat(server.calls("DELETE /v2/groups/GROUP_OPENID/messages/MSG%202")).hasSize(1);
        assertThat(server.calls("DELETE /v2/users/OPENID_USER/messages/MSG%2F1").getFirst().body()).isEmpty();
    }

    @Test
    void mapsBusinessErrorCodeToException() {
        server.stub("POST /v2/users/OPENID_USER/messages", 200,
                "{\"code\":40054005,\"message\":\"消息被去重\"}");

        assertThatThrownBy(() -> api.sendToUser("OPENID_USER", SendMessageRequest.text("hi")))
                .isInstanceOf(QqApiException.class)
                .hasMessageContaining("40054005")
                .hasMessageContaining("消息被去重")
                .satisfies(exception -> assertThat(((QqApiException) exception).code()).isEqualTo(40054005))
                .satisfies(exception -> assertThat(((QqApiException) exception).httpStatus()).isEqualTo(200));
    }

    @Test
    void mapsHttpStatusToException() {
        server.stub("POST /v2/groups/GROUP_OPENID/messages", 404,
                "{\"code\":11244,\"message\":\"openid invalid\"}");

        assertThatThrownBy(() -> api.sendToGroup("GROUP_OPENID", SendMessageRequest.text("hi")))
                .isInstanceOf(QqApiException.class)
                .hasMessageContaining("HTTP 404")
                .satisfies(exception -> assertThat(((QqApiException) exception).httpStatus()).isEqualTo(404));
    }

    @Test
    void refreshesTokenAndRetriesOnceOnUnauthorized() {
        server.stub("POST /v2/users/OPENID_USER/messages",
                java.util.Map.entry(401, "{\"code\":11244,\"message\":\"invalid token\"}"),
                java.util.Map.entry(200, "{\"id\":\"m1\"}"));
        server.stub("POST /app/getAppAccessToken", 200,
                "{\"access_token\":\"second-token\",\"expires_in\":\"7200\"}");

        assertThat(api.sendToUser("OPENID_USER", SendMessageRequest.text("hi")).id()).isEqualTo("m1");

        List<FakeQqApiServer.Call> attempts = server.calls("POST /v2/users/OPENID_USER/messages");
        assertThat(attempts).hasSize(2);
        assertThat(attempts.getFirst().authorization()).isEqualTo("QQBot test-token");
        assertThat(attempts.get(1).authorization()).isEqualTo("QQBot second-token");
        assertThat(server.calls("POST /app/getAppAccessToken")).hasSize(2);
    }

    @Test
    void rejectsBlankIdentifiers() {
        assertThatThrownBy(() -> api.sendToUser(" ", SendMessageRequest.text("hi")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user_openid");
        assertThatThrownBy(() -> api.recallFromGroup("GROUP_OPENID", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message_id");
        assertThat(server.calls("POST /app/getAppAccessToken")).isEmpty();
    }
}
