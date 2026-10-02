package love.aira.kohaku.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.AccessTokenProvider;
import love.aira.kohaku.api.FakeQqApiServer;
import love.aira.kohaku.api.QqMessageApi;
import love.aira.kohaku.api.QqOpenApiClient;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.config.KohakuConfig;
import love.aira.kohaku.config.QqIntent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupMessageCreateEvent;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.gateway.event.model.InteractionData;
import love.aira.kohaku.gateway.event.model.InteractionResolved;
import love.aira.kohaku.gateway.event.model.MessageAuthor;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import love.aira.kohaku.reply.BotReplies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 「命令进入 + 状态驱动按钮」骨架：入口匹配、状态解析、按钮状态驱动、装配期校验。
 *
 * <p>用真实 {@link BotReplies} + 假 HTTP 服务，断言的是**真正发出的报文**。
 */
class ButtonFeatureTest {

    private static final String TOKEN_BODY = "{\"access_token\":\"test-token\",\"expires_in\":\"7200\"}";
    private static final String SENT = "{\"id\":\"ROBOT1.0_sent\",\"timestamp\":\"2026-10-02T10:00:00+08:00\"}";
    private static final String USER_PATH = "POST /v2/users/USER_OPENID/messages";
    private static final String GROUP_PATH = "POST /v2/groups/GROUP_OPENID/messages";

    private final List<Map<String, String>> rendered = new ArrayList<>();

    private FakeQqApiServer server;
    private BotReplies replies;

    @BeforeEach
    void setUp() {
        server = new FakeQqApiServer();
        JsonMapper mapper = new JsonMapper();
        KohakuConfig config = new KohakuConfig("app-id", "app-secret", server.baseUrl(),
                server.baseUrl() + "/app/getAppAccessToken", List.of(QqIntent.PUBLIC_GUILD_MESSAGES), 0, 1,
                "kohaku", true, Duration.ofSeconds(1), Duration.ofSeconds(60), false);
        server.stub("POST /app/getAppAccessToken", 200, TOKEN_BODY);
        AccessTokenProvider tokens = new AccessTokenProvider(HttpClient.newHttpClient(), mapper, config);
        replies = new BotReplies(new QqMessageApi(new QqOpenApiClient(HttpClient.newHttpClient(), mapper, config, tokens)));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** 被测骨架：/card 命令 + 「文本 → 页码状态」与「状态 → 消息」两个纯函数。 */
    private BotFeature feature() {
        return ButtonFeature.of("card")
                .commands("/card")
                .state(text -> {
                    int page = text.length() > 6 ? Integer.parseInt(text.substring(6).trim()) : 1;
                    return Map.of("p", Integer.toString(page));
                })
                .render(state -> {
                    rendered.add(state);
                    return SendMessageRequest.markdown("# 第 " + state.get("p") + " 页");
                })
                .build(replies);
    }

    @Test
    void answersMatchingCommandWithRenderedState() {
        server.stub(USER_PATH, 200, SENT);

        assertThat(c2cEntry(feature()).handle(c2cMessage("/card 3"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(rendered).containsExactly(Map.of("p", "3"));
        assertThat(server.calls(USER_PATH).getFirst().body())
                .contains("\"msg_id\":\"MSG_1\"").contains("# 第 3 页");
    }

    @Test
    void ignoresTextThatIsNotItsCommand() {
        BotFeature feature = feature();

        assertThat(c2cEntry(feature).handle(c2cMessage("你好"))).isEqualTo(HandlerResult.IGNORED);
        assertThat(c2cEntry(feature).handle(c2cMessage("/cards"))).isEqualTo(HandlerResult.IGNORED);   // 前缀要整词
        assertThat(c2cEntry(feature).handle(c2cMessage("/other"))).isEqualTo(HandlerResult.IGNORED);
        assertThat(groupEntry(feature).handle(groupMessage("/other"))).isEqualTo(HandlerResult.IGNORED);

        assertThat(rendered).isEmpty();
        assertThat(server.calls()).isEmpty();
    }

    @Test
    void groupEntryAlsoWorks() {
        server.stub(GROUP_PATH, 200, SENT);

        assertThat(groupEntry(feature()).handle(groupMessage("/card 2"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(server.calls(GROUP_PATH).getFirst().body())
                .contains("\"msg_id\":\"MSG_G\"").contains("# 第 2 页");
    }

    /** 群内全量消息（平台为该群开通「接收全量信息」后下发 GROUP_MESSAGE_CREATE）：入口行为与 @ 消息一致。 */
    @Test
    void fullGroupMessageEntryAlsoWorks() {
        server.stub(GROUP_PATH, 200, SENT);

        assertThat(fullGroupEntry(feature()).handle(fullGroupMessage("/card 2"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(server.calls(GROUP_PATH).getFirst().body())
                .contains("\"msg_id\":\"MSG_G\"").contains("# 第 2 页");
    }

    @Test
    void defaultCommandIsSlashId() {
        server.stub(USER_PATH, 200, SENT);      // 假服务按次消费 stub，两次调用要两条
        server.stub(USER_PATH, 200, SENT);
        BotFeature feature = ButtonFeature.of("vote")
                .state(text -> Map.of("q", "1"))
                .render(state -> SendMessageRequest.text("投票"))
                .build(replies);

        assertThat(c2cEntry(feature).handle(c2cMessage("/vote"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(c2cEntry(feature).handle(c2cMessage("/vote now"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(c2cEntry(feature).handle(c2cMessage("/voter"))).isEqualTo(HandlerResult.IGNORED);

        assertThat(server.calls(USER_PATH)).hasSize(2);
    }

    @Test
    void buttonClickRendersFromButtonStateAndRepliesToTheInteraction() {
        server.stub(USER_PATH, 200, SENT);

        HandlerResult result = button(feature(), "next")
                .onButton(context("card:next;p=2", Map.of("p", "2")));

        assertThat(result).isEqualTo(HandlerResult.CONSUMED);
        assertThat(rendered).containsExactly(Map.of("p", "2"));      // 状态来自按钮 data，不依赖会话
        assertThat(server.calls(USER_PATH).getFirst().body())
                .contains("\"event_id\":\"INTERACTION_CREATE:EVENT_ID\"").contains("# 第 2 页");
    }

    @Test
    void defaultsToNextPrevPageActions() {
        assertThat(feature().buttonHandlers()).extracting(ButtonHandler::action)
                .containsExactly("next", "prev", "page");
    }

    @Test
    void customActionsReplaceDefaults() {
        BotFeature feature = ButtonFeature.of("vote")
                .state(text -> Map.of("q", "1"))
                .render(state -> SendMessageRequest.text("投票"))
                .actions("yes", "no")
                .build(replies);

        assertThat(feature.buttonHandlers()).extracting(ButtonHandler::action).containsExactly("yes", "no");
    }

    @Test
    void rejectsIncompleteConfigurationAtBuildTime() {
        assertThatThrownBy(() -> ButtonFeature.of("vote").render(state -> SendMessageRequest.text("x")).build(replies))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("state");
        assertThatThrownBy(() -> ButtonFeature.of("vote").state(text -> Map.of()).build(replies))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("render");
        assertThatThrownBy(() -> ButtonFeature.of("bad id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("featureId");
        assertThatThrownBy(() -> ButtonFeature.of("vote").commands("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("命令前缀");
        assertThatThrownBy(() -> ButtonFeature.of("vote").actions())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("按钮动作");
        assertThat(server.calls()).isEmpty();
    }

    @Test
    void renderReturningNullFailsLoudlyInsteadOfSilentlyIgnoring() {
        BotFeature feature = ButtonFeature.of("vote")
                .state(text -> Map.of("q", "1"))
                .render(state -> null)
                .build(replies);

        assertThatThrownBy(() -> c2cEntry(feature).handle(c2cMessage("/vote")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("render 返回了 null");
    }

    @SuppressWarnings("unchecked")
    private static BotEventHandler<C2cMessageCreateEvent> c2cEntry(BotFeature feature) {
        return (BotEventHandler<C2cMessageCreateEvent>) feature.messageHandlers().stream()
                .filter(handler -> handler.eventType().equals(C2cMessageCreateEvent.class))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static BotEventHandler<GroupAtMessageCreateEvent> groupEntry(BotFeature feature) {
        return (BotEventHandler<GroupAtMessageCreateEvent>) feature.messageHandlers().stream()
                .filter(handler -> handler.eventType().equals(GroupAtMessageCreateEvent.class))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static BotEventHandler<GroupMessageCreateEvent> fullGroupEntry(BotFeature feature) {
        return (BotEventHandler<GroupMessageCreateEvent>) feature.messageHandlers().stream()
                .filter(handler -> handler.eventType().equals(GroupMessageCreateEvent.class))
                .findFirst()
                .orElseThrow();
    }

    private static ButtonHandler button(BotFeature feature, String action) {
        return feature.buttonHandlers().stream()
                .filter(handler -> handler.action().equals(action))
                .findFirst()
                .orElseThrow();
    }

    private static ButtonContext context(String buttonData, Map<String, String> state) {
        InteractionResolved resolved = new InteractionResolved(buttonData, "btn-1", null, null, null, null, null, null,
                null, null);
        InteractionCreate payload = new InteractionCreate("EVENT_ID", InteractionRouter.TYPE_INLINE_KEYBOARD, "c2c", 2,
                "2026-10-02T10:00:00+08:00", null, null, "USER_OPENID", null, null,
                new InteractionData(InteractionRouter.TYPE_INLINE_KEYBOARD, resolved), 1, "102012345");
        return new ButtonContext(new InteractionCreateEvent(1, "INTERACTION_CREATE:EVENT_ID", raw(), payload), "card",
                "next", state, "btn-1", buttonData, 2, "c2c", "USER_OPENID", null, null);
    }

    private static C2cMessageCreateEvent c2cMessage(String content) {
        return new C2cMessageCreateEvent(1, "C2C_MESSAGE_CREATE:ID", raw(),
                new C2cMessage("MSG_1", author(), content, null, null, null, null, null, null));
    }

    private static GroupAtMessageCreateEvent groupMessage(String content) {
        return new GroupAtMessageCreateEvent(2, "GROUP_AT_MESSAGE_CREATE:ID", raw(),
                new GroupMessage("MSG_G", author(), content, "GROUP_OPENID", null, null, null, null, null, null, null));
    }

    private static GroupMessageCreateEvent fullGroupMessage(String content) {
        return new GroupMessageCreateEvent(3, "GROUP_MESSAGE_CREATE:ID", raw(),
                new GroupMessage("MSG_G", author(), content, "GROUP_OPENID", null, null, null, null, null, null, null));
    }

    private static MessageAuthor author() {
        return new MessageAuthor("ID_1", "用户", false, null, null, null, "USER_OPENID", null, null);
    }

    private static JsonNode raw() {
        return new JsonMapper().readTree("{}");
    }
}
