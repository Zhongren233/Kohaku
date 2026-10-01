package love.aira.kohaku.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import love.aira.kohaku.api.model.ChannelMessageRequest;
import love.aira.kohaku.api.model.ChannelMessageResponse;
import love.aira.kohaku.api.model.DmsSessionResponse;
import love.aira.kohaku.api.model.MarkdownMessage;
import love.aira.kohaku.config.QqBotProperties;
import love.aira.kohaku.config.QqIntent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class QqChannelMessageApiTest {

    private static final String MESSAGE_RESPONSE = """
            {"id":"MSG_1","channel_id":"CH_1","guild_id":"GUILD_1","content":"hi","timestamp":"2026-10-01T12:00:00+08:00",
             "author":{"id":"BOT_1","username":"kohaku","avatar":"http://a","bot":true},"embeds":[{}],"pinned":false,
             "type":0,"flags":0}""";

    private FakeQqApiServer server;
    private QqChannelMessageApi api;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        server = new FakeQqApiServer();
        mapper = new JsonMapper();
        QqBotProperties properties = new QqBotProperties("app-id", "app-secret", server.baseUrl(),
                server.baseUrl() + "/app/getAppAccessToken", List.of(QqIntent.GUILDS), 0, 1,
                "kohaku", true, Duration.ofSeconds(1), Duration.ofSeconds(60), true, false);
        server.stub("POST /app/getAppAccessToken", 200,
                "{\"access_token\":\"test-token\",\"expires_in\":\"7200\"}");
        HttpClient httpClient = HttpClient.newHttpClient();
        AccessTokenProvider tokens = new AccessTokenProvider(httpClient, mapper, properties);
        api = new QqChannelMessageApi(new QqOpenApiClient(httpClient, mapper, properties, tokens));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendsChannelMessageAndParsesMessageObject() {
        server.stub("POST /channels/CH_1/messages", 200, MESSAGE_RESPONSE);

        ChannelMessageResponse response = api.sendToChannel("CH_1",
                ChannelMessageRequest.text("hi").replyingTo("EVENT_MSG_ID").quoting("REFIDX_1"));

        assertThat(response.id()).isEqualTo("MSG_1");
        assertThat(response.channelId()).isEqualTo("CH_1");
        assertThat(response.author().username()).isEqualTo("kohaku");
        assertThat(response.pinned()).isFalse();

        FakeQqApiServer.Call call = server.calls("POST /channels/CH_1/messages").getFirst();
        assertThat(call.authorization()).isEqualTo("QQBot test-token");
        assertThat(call.contentType()).startsWith("application/json");
        assertThat(mapper.readTree(call.body())).isEqualTo(mapper.readTree("""
                {"content":"hi","message_reference":{"message_id":"REFIDX_1"},"msg_id":"EVENT_MSG_ID"}"""));
    }

    @Test
    void sendsChannelMessageWithMarkdownAndArk() {
        server.stub("POST /channels/CH_1/messages", 200, MESSAGE_RESPONSE);

        api.sendToChannel("CH_1", ChannelMessageRequest.markdown(MarkdownMessage.of("# 标题")));

        assertThat(mapper.readTree(server.calls("POST /channels/CH_1/messages").getFirst().body()))
                .isEqualTo(mapper.readTree("{\"markdown\":{\"content\":\"# 标题\"}}"));
    }

    @Test
    void uploadsImageAsMultipartFormData(@TempDir Path tempDir) throws Exception {
        server.stub("POST /channels/CH_1/messages", 200, MESSAGE_RESPONSE);
        Path image = tempDir.resolve("pic.png");
        Files.writeString(image, "PNG-BYTES");

        api.sendImageToChannel("CH_1", ChannelMessageRequest.text("看图"), image);

        FakeQqApiServer.Call call = server.calls("POST /channels/CH_1/messages").getFirst();
        assertThat(call.contentType()).startsWith("multipart/form-data; boundary=");
        assertThat(call.body())
                .contains("Content-Disposition: form-data; name=\"content\"")
                .contains("看图")
                .contains("Content-Disposition: form-data; name=\"file_image\"; filename=\"pic.png\"")
                .contains("Content-Type: image/png")
                .contains("PNG-BYTES");
        // JSON 字段不应出现在 form-data 中
        assertThat(call.body()).doesNotContain("\"content\":");
    }

    @Test
    void recallsChannelMessageWithHideTipFlag() {
        server.stub("DELETE /channels/CH_1/messages/MSG_1", 200, "{}");
        server.stub("DELETE /channels/CH_1/messages/MSG_1", 200, "{}");

        api.recallFromChannel("CH_1", "MSG_1");
        api.recallFromChannel("CH_1", "MSG_1", true);

        assertThat(server.calls("DELETE /channels/CH_1/messages/MSG_1")).hasSize(2);
        assertThat(server.calls("DELETE /channels/CH_1/messages/MSG_1").stream()
                .map(FakeQqApiServer.Call::query)).containsExactly("hidetip=false", "hidetip=true");
    }

    @Test
    void createsDmsSessionAndSendsDm() {
        server.stub("POST /users/@me/dms", 200,
                "{\"guild_id\":\"DMS_1\",\"channel_id\":\"DMS_CH_1\",\"create_time\":\"1642545606\"}");
        server.stub("POST /dms/DMS_1/messages", 200, MESSAGE_RESPONSE);
        server.stub("DELETE /dms/DMS_1/messages/MSG_1", 200, "{}");

        DmsSessionResponse session = api.createDmsSession("USER_1", "GUILD_1");
        assertThat(session.guildId()).isEqualTo("DMS_1");
        assertThat(session.channelId()).isEqualTo("DMS_CH_1");
        assertThat(session.createTime()).isEqualTo("1642545606");
        assertThat(mapper.readTree(server.calls("POST /users/@me/dms").getFirst().body()))
                .isEqualTo(mapper.readTree("{\"recipient_id\":\"USER_1\",\"source_guild_id\":\"GUILD_1\"}"));

        ChannelMessageResponse dm = api.sendToDms(session.guildId(), ChannelMessageRequest.image("http://i"));
        assertThat(dm.id()).isEqualTo("MSG_1");
        assertThat(mapper.readTree(server.calls("POST /dms/DMS_1/messages").getFirst().body()))
                .isEqualTo(mapper.readTree("{\"image\":\"http://i\"}"));

        api.recallFromDms("DMS_1", "MSG_1", true);
        assertThat(server.calls("DELETE /dms/DMS_1/messages/MSG_1")).hasSize(1);
        assertThat(server.calls("DELETE /dms/DMS_1/messages/MSG_1").getFirst().query()).isEqualTo("hidetip=true");
    }
}
