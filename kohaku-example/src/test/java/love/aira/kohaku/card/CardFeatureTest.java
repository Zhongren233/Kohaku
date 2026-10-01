package love.aira.kohaku.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.feature.ButtonContext;
import love.aira.kohaku.feature.ButtonHandler;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.gateway.event.model.InteractionData;
import love.aira.kohaku.gateway.event.model.InteractionResolved;
import love.aira.kohaku.gateway.event.model.MessageAuthor;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import love.aira.kohaku.reply.BotReplies;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** 示例功能 `\\/card`：分页渲染、命令入口与翻页按钮回调。 */
class CardFeatureTest {

    private final BotReplies replies = mock(BotReplies.class);
    private final BotFeature feature = new CardFeature().card(replies);
    private final ArgumentCaptor<BotEvent> repliedEvent = ArgumentCaptor.forClass(BotEvent.class);
    private final ArgumentCaptor<SendMessageRequest> repliedRequest = ArgumentCaptor.forClass(SendMessageRequest.class);

    @Test
    void rendersFirstPageWithNextButtonOnly() {
        SendMessageRequest request = CardFeature.page(1);

        // 实测：键盘只在 markdown 消息（msg_type=2）上渲染，纯文本会被平台丢弃
        assertThat(request.msgType()).isEqualTo(2);
        assertThat(request.content()).isNull();
        assertThat(request.keyboard()).isNotNull();

        assertThat(request.markdown().content())
                .contains("卡片列表 1/3")
                .contains("C-001").contains("C-005")
                .doesNotContain("C-006");
        assertThat(buttonIds(request)).containsExactly("page", "next");
        if (CardFeature.USE_INTERACTION_BUTTONS) {
            assertThat(buttonData(request, 1)).isEqualTo("card:next:p=2");   // 回调按钮：平台下发互动事件
            assertThat(buttonType(request, 1)).isEqualTo(1);
        } else {
            assertThat(buttonData(request, 1)).isEqualTo("/card next 2");    // 指令按钮：点击即发一条命令消息
            assertThat(buttonType(request, 1)).isEqualTo(2);
        }
    }

    @Test
    void rendersLastPageWithoutNextButtonAndClampsOverflow() {
        SendMessageRequest last = CardFeature.page(3);
        assertThat(last.markdown().content()).contains("卡片列表 3/3").contains("C-011").contains("C-012");
        assertThat(buttonIds(last)).containsExactly("prev", "page");
        assertThat(buttonData(last, 0)).isEqualTo(CardFeature.USE_INTERACTION_BUTTONS ? "card:prev:p=2" : "/card prev 2");

        assertThat(CardFeature.page(99).markdown().content()).contains("卡片列表 3/3");   // 越界页码被夹回
    }

    @Test
    void commandEntryRepliesWithFirstPage() {
        BotEventHandler<C2cMessageCreateEvent> entry = c2cEntry();
        C2cMessageCreateEvent event = c2cMessage("/card");

        assertThat(entry.handle(event)).isEqualTo(HandlerResult.CONSUMED);

        SendMessageRequest sent = captureReplied();
        assertThat(sent.markdown().content()).contains("卡片列表 1/3");
        assertThat(repliedEvent.getValue()).isSameAs(event);   // 按事件回复，目标与被动标记由 BotReplies 解析
    }

    @Test
    void commandEntryIgnoresOtherMessages() {
        BotEventHandler<C2cMessageCreateEvent> entry = c2cEntry();

        assertThat(entry.handle(c2cMessage("你好"))).isEqualTo(HandlerResult.IGNORED);
        assertThat(entry.handle(c2cMessage(null))).isEqualTo(HandlerResult.IGNORED);
        assertThat(entry.handle(c2cMessage("/other"))).isEqualTo(HandlerResult.IGNORED);
        verifyNoInteractions(replies);
    }

    @Test
    void nextButtonRendersFollowingPageAndRepliesToTheInteraction() {
        assertThat(button("next").onButton(context("card:next:p=2", Map.of("p", "2"))))
                .isEqualTo(HandlerResult.CONSUMED);

        SendMessageRequest sent = captureReplied();
        assertThat(sent.markdown().content()).contains("卡片列表 2/3").contains("C-006").contains("C-010");
        assertThat(buttonIds(sent)).containsExactly("prev", "page", "next");   // 第二页前后都有
    }

    @Test
    void pageButtonReRendersCurrentPage() {
        assertThat(button("page").onButton(context("card:page:p=2", Map.of("p", "2"))))
                .isEqualTo(HandlerResult.CONSUMED);

        assertThat(captureReplied().markdown().content()).contains("卡片列表 2/3");
    }

    @Test
    void commandButtonClickRendersTargetPage() {
        BotEventHandler<C2cMessageCreateEvent> entry = c2cEntry();

        // 指令按钮点击后，客户端会把 data 作为普通消息发出
        assertThat(entry.handle(c2cMessage("/card next 2"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(captureReplied().markdown().content()).contains("卡片列表 2/3");
    }

    @Test
    void recognizesCommandWithArguments() {
        assertThat(CardFeature.requestedPage("/card")).isEqualTo(1);
        assertThat(CardFeature.requestedPage("/card next 3")).isEqualTo(3);
        assertThat(CardFeature.requestedPage("/card 2")).isEqualTo(2);
        assertThat(CardFeature.requestedPage("/card bogus 2")).isZero();
        assertThat(CardFeature.requestedPage("你好")).isZero();
        assertThat(CardFeature.isCommand("/card")).isTrue();
        assertThat(CardFeature.isCommand("  /card 3 ")).isTrue();
        assertThat(CardFeature.isCommand("/cards")).isFalse();
        assertThat(CardFeature.isCommand(null)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private BotEventHandler<C2cMessageCreateEvent> c2cEntry() {
        return (BotEventHandler<C2cMessageCreateEvent>) feature.messageHandlers().getFirst();
    }

    private ButtonHandler button(String action) {
        return feature.buttonHandlers().stream()
                .filter(handler -> handler.action().equals(action))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未注册的按钮动作: " + action));
    }

    /** 捕获「功能 → BotReplies」的一次回复。目标选择与 msg_id/msg_seq/event_id 由 BotReplies 负责（见 BotRepliesTest）。 */
    private SendMessageRequest captureReplied() {
        verify(replies, atLeastOnce()).send(repliedEvent.capture(), repliedRequest.capture());
        return repliedRequest.getValue();
    }

    private static List<String> buttonIds(SendMessageRequest request) {
        return request.keyboard().content().rows().getFirst().buttons().stream()
                .map(Keyboard.Button::id)
                .toList();
    }

    private static Integer buttonType(SendMessageRequest request, int index) {
        return request.keyboard().content().rows().getFirst().buttons().get(index).action().type();
    }

    private static String buttonData(SendMessageRequest request, int index) {
        return request.keyboard().content().rows().getFirst().buttons().get(index).action().data();
    }

    private static C2cMessageCreateEvent c2cMessage(String content) {
        MessageAuthor author = new MessageAuthor("id", "nick", false, null, null, null, "USER_OPENID", null, null);
        C2cMessage message = new C2cMessage("MSG_1", author, content, "2026-10-02T00:00:00+08:00", 0, null, null, null,
                null);
        return new C2cMessageCreateEvent(1, "C2C_MESSAGE_CREATE:EVENT_ID", new JsonMapper().readTree("{}"), message);
    }

    private static ButtonContext context(String buttonData, Map<String, String> state) {
        InteractionResolved resolved = new InteractionResolved(buttonData, "btn-1", null, null, null, null, null, null,
                null, null);
        InteractionCreate payload = new InteractionCreate("EVENT_ID", 11, "c2c", 2, "2026-10-02T00:00:00+08:00",
                null, null, "USER_OPENID", null, null, new InteractionData(11, resolved), 1, "102012345");
        InteractionCreateEvent event = new InteractionCreateEvent(1, "INTERACTION_CREATE:EVENT_ID", new JsonMapper().readTree("{}"), payload);
        return new ButtonContext(event, "card", "next", state, "btn-1", buttonData, 2, "c2c", "USER_OPENID", null, null);
    }
}
