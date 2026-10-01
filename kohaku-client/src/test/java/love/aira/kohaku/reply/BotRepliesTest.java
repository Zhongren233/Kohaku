package love.aira.kohaku.reply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import love.aira.kohaku.api.AccessTokenProvider;
import love.aira.kohaku.api.FakeQqApiServer;
import love.aira.kohaku.api.QqChannelMessageApi;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.QqOpenApiClient;
import love.aira.kohaku.api.model.ChannelMessageRequest;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.config.KohakuConfig;
import love.aira.kohaku.config.QqIntent;
import love.aira.kohaku.gateway.event.AtMessageCreateEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.DirectMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAddRobotEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.ChannelMessage;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.gateway.event.model.MessageAuthor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 统一回复入口：目标解析、被动标记（msg_id+自增 msg_seq / event_id）、频道与私信路由、非法事件拒绝。
 *
 * <p>断言的是**真实发出的报文**（fake server 记录 body），而不是内部状态。
 */
class BotRepliesTest {

    private static final String TOKEN_BODY = "{\"access_token\":\"test-token\",\"expires_in\":\"7200\"}";
    private static final String SENT = "{\"id\":\"ROBOT1.0_sent\",\"timestamp\":\"2026-10-02T10:00:00+08:00\"}";

    private FakeQqApiServer server;
    private JsonMapper mapper;
    private ListAppender<ILoggingEvent> logs;
    private BotReplies replies;
    private BotReplies withoutChannelApi;

    @BeforeEach
    void setUp() {
        server = new FakeQqApiServer();
        mapper = new JsonMapper();
        KohakuConfig config = new KohakuConfig("app-id", "app-secret", server.baseUrl(),
                server.baseUrl() + "/app/getAppAccessToken", List.of(QqIntent.PUBLIC_GUILD_MESSAGES), 0, 1,
                "kohaku", true, Duration.ofSeconds(1), Duration.ofSeconds(60), false);
        server.stub("POST /app/getAppAccessToken", 200, TOKEN_BODY);
        AccessTokenProvider tokens = new AccessTokenProvider(HttpClient.newHttpClient(), mapper, config);
        QqOpenApiClient client = new QqOpenApiClient(HttpClient.newHttpClient(), mapper, config, tokens);
        replies = new BotReplies(new QqMessageApi(client), new QqChannelMessageApi(client));
        withoutChannelApi = new BotReplies(new QqMessageApi(client));

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(BotReplies.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(BotReplies.class)).detachAppender(logs);
        server.close();
    }

    /** 平台限制前的预警（第 4 次用满、第 5 次超限都要 WARN，但不阻断发送）。 */
    @Test
    void warnsBeforePlatformRejectsAndStillSends() {
        for (int i = 0; i < 5; i++) {
            server.stub("POST /v2/users/OPENID_USER/messages", 200, SENT);
        }

        C2cMessageCreateEvent event = c2c("MSG_1");
        for (int i = 1; i <= 5; i++) {
            replies.text(event, "第 " + i + " 次");
        }

        assertThat(server.calls("POST /v2/users/OPENID_USER/messages")).hasSize(5);   // 告警不阻断
        assertThat(warnings()).anySatisfy(warning -> assertThat(warning).contains("已用满").contains("4 次"));
        assertThat(warnings()).anySatisfy(warning -> assertThat(warning).contains("超过平台上限"));
    }

    private List<String> warnings() {
        return logs.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void c2cReplyCarriesMsgIdAndAutoIncrementingSeq() {
        server.stub("POST /v2/users/OPENID_USER/messages", 200, SENT);
        server.stub("POST /v2/users/OPENID_USER/messages", 200, SENT);

        C2cMessageCreateEvent event = c2c("MSG_1");
        assertThat(replies.text(event, "第一条").id()).isEqualTo("ROBOT1.0_sent");
        replies.text(event, "第二条");

        List<FakeQqApiServer.Call> calls = server.calls("POST /v2/users/OPENID_USER/messages");
        assertThat(calls.get(0).body())
                .contains("\"msg_id\":\"MSG_1\"").contains("\"msg_seq\":1")
                .contains("\"msg_type\":0").contains("第一条");
        // 同一 msg_id 第二次回复自动递增 msg_seq，避免平台「相同 msg_id+msg_seq」报错
        assertThat(calls.get(1).body()).contains("\"msg_seq\":2");
    }

    @Test
    void groupReplyGoesToGroupOpenid() {
        server.stub("POST /v2/groups/GROUP_OPENID/messages", 200, SENT);

        replies.text(groupAt("MSG_G"), "群回复");

        assertThat(server.calls("POST /v2/groups/GROUP_OPENID/messages").getFirst().body())
                .contains("\"msg_id\":\"MSG_G\"").contains("\"msg_seq\":1").contains("群回复");
    }

    @Test
    void interactionReplyUsesOutermostEventIdNotTheInteractionId() {
        server.stub("POST /v2/users/OPENID_USER/messages", 200, SENT);

        replies.markdown(interaction(1, "OPENID_USER", null, null), "# 结果");

        String body = server.calls("POST /v2/users/OPENID_USER/messages").getFirst().body();
        assertThat(body).contains("\"event_id\":\"INTERACTION_CREATE:EVENT_ID\"")
                .contains("\"msg_type\":2")
                .doesNotContain("msg_id").doesNotContain("msg_seq");
    }

    @Test
    void interactionReplyInGroupGoesToGroup() {
        server.stub("POST /v2/groups/GROUP_OPENID/messages", 200, SENT);

        replies.text(interaction(1, null, "GROUP_OPENID", null), "群互动结果");

        assertThat(server.calls("POST /v2/groups/GROUP_OPENID/messages").getFirst().body())
                .contains("\"event_id\":\"INTERACTION_CREATE:EVENT_ID\"");
    }

    @Test
    void markdownWithKeyboardIsSentAsMarkdownMessage() {
        server.stub("POST /v2/users/OPENID_USER/messages", 200, SENT);

        replies.markdown(c2c("MSG_1"), "# 标题", Keyboard.of(
                Keyboard.Row.of(Keyboard.Button.callback("next", "下一页", "card:next:p=2"))));

        String body = server.calls("POST /v2/users/OPENID_USER/messages").getFirst().body();
        assertThat(body).contains("\"msg_type\":2").contains("card:next:p=2").contains("# 标题");
    }

    @Test
    void callerSpecifiedPassiveMarkerIsNotOverridden() {
        server.stub("POST /v2/users/OPENID_USER/messages", 200, SENT);

        replies.send(c2c("MSG_1"), SendMessageRequest.text("自定义").replyingTo("OTHER_MSG", 9));

        String body = server.calls("POST /v2/users/OPENID_USER/messages").getFirst().body();
        assertThat(body).contains("\"msg_id\":\"OTHER_MSG\"").contains("\"msg_seq\":9");
    }

    @Test
    void channelAndDirectMessageRepliesUseChannelApi() {
        server.stub("POST /channels/CHANNEL_1/messages", 200, SENT);
        server.stub("POST /dms/GUILD_DM/messages", 200, SENT);

        replies.sendToChannel(at("MSG_CH"), ChannelMessageRequest.text("频道回复"));
        replies.sendToChannel(directMessage("MSG_DM"), ChannelMessageRequest.text("私信回复"));

        assertThat(server.calls("POST /channels/CHANNEL_1/messages").getFirst().body())
                .contains("\"msg_id\":\"MSG_CH\"").contains("频道回复");
        assertThat(server.calls("POST /dms/GUILD_DM/messages").getFirst().body())
                .contains("\"msg_id\":\"MSG_DM\"").contains("私信回复");
    }

    @Test
    void channelReplyInGuildInteractionUsesEventId() {
        server.stub("POST /channels/CHANNEL_1/messages", 200, SENT);

        replies.sendToChannel(interaction(0, null, null, "CHANNEL_1"), ChannelMessageRequest.text("频道互动"));

        assertThat(server.calls("POST /channels/CHANNEL_1/messages").getFirst().body())
                .contains("\"event_id\":\"INTERACTION_CREATE:EVENT_ID\"").doesNotContain("msg_id");
    }

    @Test
    void channelReplyWithoutChannelApiFailsFast() {
        assertThatThrownBy(() -> withoutChannelApi.sendToChannel(at("MSG_CH"), ChannelMessageRequest.text("x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QqChannelMessageApi");
    }

    @Test
    void unsupportedEventsAreRejectedInsteadOfSilentlyDropped() {
        GroupAddRobotEvent lifecycle = new GroupAddRobotEvent(1, "GROUP_ADD_ROBOT:ID", raw(), null);

        assertThatThrownBy(() -> replies.text(lifecycle, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持单聊/群聊回复");
        assertThatThrownBy(() -> replies.sendToChannel(c2c("MSG_1"), ChannelMessageRequest.text("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持频道/私信回复");
        assertThat(server.calls("POST /v2/users/OPENID_USER/messages")).isEmpty();
    }

    private JsonNode raw() {
        return mapper.readTree("{}");
    }

    private C2cMessageCreateEvent c2c(String messageId) {
        MessageAuthor author = author("OPENID_USER");
        return new C2cMessageCreateEvent(1, "C2C_MESSAGE_CREATE:ID", raw(),
                new C2cMessage(messageId, author, "/card", "2026-10-02T10:00:00+08:00", 0, null, null, null, null));
    }

    private GroupAtMessageCreateEvent groupAt(String messageId) {
        MessageAuthor author = author("MEMBER_OPENID");
        return new GroupAtMessageCreateEvent(2, "GROUP_AT_MESSAGE_CREATE:ID", raw(),
                new GroupMessage(messageId, author, "/card", "GROUP_OPENID", "2026-10-02T10:00:00+08:00", 0, null, null,
                        null, null, null));
    }

    private AtMessageCreateEvent at(String messageId) {
        return new AtMessageCreateEvent(3, "AT_MESSAGE_CREATE:ID", raw(),
                channelMessage(messageId, "CHANNEL_1", "GUILD_1"));
    }

    private DirectMessageCreateEvent directMessage(String messageId) {
        return new DirectMessageCreateEvent(4, "DIRECT_MESSAGE_CREATE:ID", raw(),
                channelMessage(messageId, "CHANNEL_DM", "GUILD_DM"));
    }

    private static ChannelMessage channelMessage(String messageId, String channelId, String guildId) {
        return new ChannelMessage(messageId, channelId, guildId, "/card", "2026-10-02T10:00:00+08:00", null, null,
                author("USER_1"), null, null, null, null, null, null, null, null);
    }

    private InteractionCreateEvent interaction(Integer chatType, String userOpenid, String groupOpenid,
                                              String channelId) {
        return new InteractionCreateEvent(6, "INTERACTION_CREATE:EVENT_ID", raw(),
                new InteractionCreate("INTERACTION_ID", 11, "card", chatType, "2026-10-02T10:00:00+08:00", "GUILD_1",
                        channelId, userOpenid, groupOpenid, null, null, 1, "APP_ID"));
    }

    private static MessageAuthor author(String openid) {
        return new MessageAuthor("ID_1", "用户", false, null, null, null, openid, "MEMBER_1", null);
    }
}
