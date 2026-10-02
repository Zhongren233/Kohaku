package love.aira.kohaku.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.feature.FeatureContext;
import love.aira.kohaku.feature.ButtonHandler;
import love.aira.kohaku.interaction.InteractionRouter;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** 示例功能 {@code /todo}：子命令 add/list/done + 列表「完成」按钮。 */
class TodoFeatureTest {

    private final BotReplies replies = mock(BotReplies.class);
    private final BotFeature feature = new TodoFeature().todo(replies);
    private final ArgumentCaptor<BotEvent> repliedEvent = ArgumentCaptor.forClass(BotEvent.class);
    private final ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<String> markdownCaptor = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<Keyboard> keyboardCaptor = ArgumentCaptor.forClass(Keyboard.class);

    @Test
    void bareCommandRepliesWithHelp() {
        assertThat(entry().handle(c2c("/todo"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(capturedText()).contains("add").contains("list").contains("done");
    }

    @Test
    void addRepliesWithListAndOneButtonPerItem() {
        entry().handle(c2c("/todo add 买菜 和 牛奶"));
        assertThat(capturedMarkdown()).contains("已添加 #1 买菜 和 牛奶").contains("#1 买菜 和 牛奶");
        assertThat(buttonLabels(capturedKeyboard())).containsExactly("完成 #1");

        entry().handle(c2c("/todo add 写周报"));
        assertThat(capturedMarkdown()).contains("#1 买菜 和 牛奶").contains("#2 写周报");
        assertThat(buttonLabels(capturedKeyboard())).containsExactly("完成 #1", "完成 #2");
    }

    @Test
    void doneByTextRemovesItemAndRendersRemainingList() {
        entry().handle(c2c("/todo add 买菜"));
        entry().handle(c2c("/todo add 写周报"));

        assertThat(entry().handle(c2c("/todo done 1"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(capturedMarkdown()).contains("已完成 #1 买菜").contains("#2 写周报");
        assertThat(buttonLabels(capturedKeyboard())).containsExactly("完成 #2");
    }

    @Test
    void doneButtonClickRemovesItemAndRepliesToTheInteraction() {
        entry().handle(c2c("/todo add 买菜"));
        entry().handle(c2c("/todo add 写周报"));

        FeatureContext click = context("todo:done:id=1", Map.of(TodoFeature.STATE_ID, "1"));
        assertThat(button(TodoFeature.ACTION_DONE).onButton(click)).isEqualTo(HandlerResult.CONSUMED);

        assertThat(capturedMarkdown()).contains("#2 写周报");
        assertThat(buttonLabels(capturedKeyboard())).containsExactly("完成 #2");
        assertThat(repliedEvent.getValue()).isSameAs(click.interaction());   // 回复目标就是这次互动
    }

    /** 端到端：按钮 data 经 {@link InteractionRouter} 解码后按「功能名/动作」投递回本功能。 */
    @Test
    void interactionRouterDeliversButtonToThisFeature() {
        entry().handle(c2c("/todo add 买菜"));
        entry().handle(c2c("/todo add 写周报"));

        InteractionRouter router = new InteractionRouter(List.of(feature));

        assertThat(router.handle(interaction("todo:done:id=1"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(capturedMarkdown()).contains("#2 写周报");
        assertThat(buttonLabels(capturedKeyboard())).containsExactly("完成 #2");
    }

    @Test
    void doneRejectsUnknownOrMissingId() {
        entry().handle(c2c("/todo add 买菜"));

        entry().handle(c2c("/todo done 9"));
        assertThat(capturedText()).contains("没有 #9");

        entry().handle(c2c("/todo done abc"));
        assertThat(capturedText()).contains("用法");
    }

    @Test
    void completingLastItemClearsList() {
        entry().handle(c2c("/todo add 买菜"));

        assertThat(entry().handle(c2c("/todo done 1"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(capturedText()).contains("已完成 #1 买菜").contains("待办为空");
    }

    @Test
    void listOnEmptyRepliesWithHint() {
        assertThat(entry().handle(c2c("/todo list"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(capturedText()).contains("待办为空");
    }

    @Test
    void addWithoutContentOrUnknownSubcommandExplainsUsage() {
        entry().handle(c2c("/todo add"));
        assertThat(capturedText()).contains("用法");

        assertThat(entry().handle(c2c("/todo nope"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(capturedText()).contains("未知子命令").contains("nope");
    }

    @Test
    void ignoresTextThatIsNotItsCommand() {
        assertThat(entry().handle(c2c("/other"))).isEqualTo(HandlerResult.IGNORED);
        assertThat(entry().handle(c2c("/todos"))).isEqualTo(HandlerResult.IGNORED);
        assertThat(entry().handle(c2c("你好"))).isEqualTo(HandlerResult.IGNORED);
        assertThat(entry().handle(c2c(null))).isEqualTo(HandlerResult.IGNORED);
    }

    /** 命令词与子命令用任意空白分隔：匹配与切分共用同一次分词。 */
    @Test
    void acceptsAnyWhitespaceBetweenCommandAndSubcommand() {
        assertThat(entry().handle(c2c("/todo\tadd 空格"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(capturedMarkdown()).contains("#1 空格");
    }

    /** 按钮形态随「互动事件」权限选择：回调按钮携带 {@code todo:done:id=N}，指令按钮发 {@code /todo done N}。 */
    @Test
    void buttonDataFollowsInteractionPermission() {
        entry().handle(c2c("/todo add x"));

        assertThat(buttonData(capturedKeyboard()).getFirst())
                .isEqualTo(TodoFeature.USE_INTERACTION_BUTTONS ? "todo:done:id=1" : "/todo done 1");
    }


    /** 按触发者隔离：同一个群里，两个人各看各的。 */
    @Test
    void todosAreIsolatedPerUser() {
        entry().handle(c2c("/todo add 甲的事", "OPENID_1"));

        assertThat(entry().handle(c2c("/todo list", "OPENID_2"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(capturedText()).contains("待办为空");

        entry().handle(c2c("/todo list", "OPENID_1"));
        assertThat(capturedMarkdown()).contains("#1 甲的事");
    }

    /** 同一个人在群里与单聊里是同一份列表：群聊的 member_openid 与单聊的 user_openid 同值。 */
    @Test
    void samePersonSharesTodosAcrossGroupAndC2c() {
        entry().handle(c2c("/todo add 买菜", "OPENID_1"));

        assertThat(groupEntry().handle(groupAt("/todo list", "OPENID_1"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(capturedMarkdown()).contains("#1 买菜");
        assertThat(buttonData(capturedKeyboard())).containsExactly("todo:done:id=1");

        // 在群里点「完成」，单聊里也应看到结果（同一份列表）
        assertThat(button(TodoFeature.ACTION_DONE).onButton(groupContext("todo:done:id=1", "OPENID_1")))
                .isEqualTo(HandlerResult.CONSUMED);
        entry().handle(c2c("/todo list", "OPENID_1"));
        assertThat(capturedText()).contains("待办为空");

        // 另一个人在群里仍看不到这条
        assertThat(groupEntry().handle(groupAt("/todo list", "OPENID_2"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(capturedText()).contains("待办为空");
    }

    /** 别人的按钮（转发/旧消息）落不到我的列表上：id 只在自己的列表里查。 */
    @Test
    void buttonFromAnotherUserDoesNotCompleteForeignItem() {
        entry().handle(c2c("/todo add 甲的事", "OPENID_1"));

        assertThat(button(TodoFeature.ACTION_DONE).onButton(context("todo:done:id=1",
                Map.of(TodoFeature.STATE_ID, "1"), "OPENID_2"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(capturedText()).contains("没有 #1");

        entry().handle(c2c("/todo list", "OPENID_1"));
        assertThat(capturedMarkdown()).contains("#1 甲的事");     // 甲的事没被删
    }

    /** 平台没下发触发者标识的异常报文：交回处理链，不炸也不误写。 */
    @Test
    void ignoresMessageWithoutSenderIdentity() {
        assertThat(entry().handle(c2c("/todo add x", null))).isEqualTo(HandlerResult.IGNORED);
    }

    @SuppressWarnings("unchecked")
    private BotEventHandler<C2cMessageCreateEvent> entry() {
        return (BotEventHandler<C2cMessageCreateEvent>) feature.messageHandlers().stream()
                .filter(handler -> handler.eventType().equals(C2cMessageCreateEvent.class))
                .findFirst()
                .orElseThrow();
    }

    private ButtonHandler button(String action) {
        return feature.buttonHandlers().stream()
                .filter(handler -> handler.action().equals(action))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未注册的按钮动作: " + action));
    }

    /** 最后一次 {@code replies.text(...)} 的内容（目标与被动标记由 BotReplies 负责）。 */
    private String capturedText() {
        verify(replies, atLeastOnce()).text(repliedEvent.capture(), textCaptor.capture());
        return textCaptor.getValue();
    }

    /** 最后一次 {@code replies.markdown(...)} 的内容。 */
    private String capturedMarkdown() {
        verify(replies, atLeastOnce()).markdown(repliedEvent.capture(), markdownCaptor.capture(),
                keyboardCaptor.capture());
        return markdownCaptor.getValue();
    }

    private Keyboard capturedKeyboard() {
        verify(replies, atLeastOnce()).markdown(repliedEvent.capture(), markdownCaptor.capture(),
                keyboardCaptor.capture());
        return keyboardCaptor.getValue();
    }

    private static List<String> buttonLabels(Keyboard keyboard) {
        return keyboard.content().rows().stream()
                .flatMap(row -> row.buttons().stream())
                .map(button -> button.renderData().label())
                .toList();
    }

    private static List<String> buttonData(Keyboard keyboard) {
        return keyboard.content().rows().stream()
                .flatMap(row -> row.buttons().stream())
                .map(button -> button.action().data())
                .toList();
    }

    private static C2cMessageCreateEvent c2c(String content) {
        return c2c(content, "USER_OPENID");
    }


    private static C2cMessageCreateEvent c2c(String content, String userOpenid) {
        MessageAuthor author = new MessageAuthor("id", "nick", false, null, null, null, userOpenid, null, null);
        C2cMessage message = new C2cMessage("MSG_1", author, content, "2026-10-02T00:00:00+08:00", 0, null, null, null,
                null);
        return new C2cMessageCreateEvent(1, "C2C_MESSAGE_CREATE:EVENT_ID", new JsonMapper().readTree("{}"), message);
    }

    /** 群 @ 消息：群场景下发的是 member_openid（与单聊的 user_openid 同值）。 */
    private static GroupAtMessageCreateEvent groupAt(String content, String memberOpenid) {
        MessageAuthor author = new MessageAuthor("id", "nick", false, null, null, null, null, memberOpenid, "member");
        GroupMessage message = new GroupMessage("MSG_G", author, content, "GROUP_OPENID", "2026-10-02T00:00:00+08:00", 0,
                null, null, null, null, null);
        return new GroupAtMessageCreateEvent(2, "GROUP_AT_MESSAGE_CREATE:EVENT_ID", new JsonMapper().readTree("{}"),
                message);
    }

    private static FeatureContext context(String buttonData, Map<String, String> state, String userOpenid) {
        return FeatureContext.ofButton(interaction(buttonData, userOpenid), TodoFeature.ID, TodoFeature.ACTION_DONE,
                state, "done-1", buttonData);
    }

    /** 群场景的按钮点击：chat_type=1，身份在 group_member_openid。 */
    private static FeatureContext groupContext(String buttonData, String groupMemberOpenid) {
        InteractionResolved resolved = new InteractionResolved(buttonData, "done-1", null, null, null, null, null, null,
                null, null);
        InteractionCreate payload = new InteractionCreate("EVENT_ID", 11, "group", 1, "2026-10-02T00:00:00+08:00",
                null, null, null, "GROUP_OPENID", groupMemberOpenid, new InteractionData(11, resolved), 1, "102012345");
        return FeatureContext.ofButton(new InteractionCreateEvent(1, "INTERACTION_CREATE:EVENT_ID",
                new JsonMapper().readTree("{}"), payload), TodoFeature.ID, TodoFeature.ACTION_DONE,
                Map.of(TodoFeature.STATE_ID, "1"), "done-1", buttonData);
    }

    private static InteractionCreateEvent interaction(String buttonData, String userOpenid) {
        InteractionResolved resolved = new InteractionResolved(buttonData, "done-1", null, null, null, null, null, null,
                null, null);
        InteractionCreate payload = new InteractionCreate("EVENT_ID", 11, "c2c", 2, "2026-10-02T00:00:00+08:00",
                null, null, userOpenid, null, null, new InteractionData(11, resolved), 1, "102012345");
        return new InteractionCreateEvent(1, "INTERACTION_CREATE:EVENT_ID", new JsonMapper().readTree("{}"), payload);
    }

    @SuppressWarnings("unchecked")
    private BotEventHandler<GroupAtMessageCreateEvent> groupEntry() {
        return (BotEventHandler<GroupAtMessageCreateEvent>) feature.messageHandlers().stream()
                .filter(handler -> handler.eventType().equals(GroupAtMessageCreateEvent.class))
                .findFirst()
                .orElseThrow();
    }

    private static InteractionCreateEvent interaction(String buttonData) {
        InteractionResolved resolved = new InteractionResolved(buttonData, "done-1", null, null, null, null, null, null,
                null, null);
        InteractionCreate payload = new InteractionCreate("EVENT_ID", 11, "c2c", 2, "2026-10-02T00:00:00+08:00",
                null, null, "USER_OPENID", null, null, new InteractionData(11, resolved), 1, "102012345");
        return new InteractionCreateEvent(1, "INTERACTION_CREATE:EVENT_ID", new JsonMapper().readTree("{}"), payload);
    }

    private static FeatureContext context(String buttonData, Map<String, String> state) {
        return context(buttonData, state, "USER_OPENID");
    }
}
