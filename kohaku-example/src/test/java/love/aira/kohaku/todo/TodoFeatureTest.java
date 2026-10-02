package love.aira.kohaku.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.MessageAuthor;
import love.aira.kohaku.gateway.handler.BotEventHandler;
import love.aira.kohaku.gateway.handler.HandlerResult;
import love.aira.kohaku.reply.BotReplies;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** 示例功能 {@code /todo}：命令词 + 子命令 add/list/done 的分派。 */
class TodoFeatureTest {

    private final BotReplies replies = mock(BotReplies.class);
    private final BotFeature feature = new TodoFeature().todo(replies);
    private final ArgumentCaptor<BotEvent> repliedEvent = ArgumentCaptor.forClass(BotEvent.class);
    private final ArgumentCaptor<String> repliedText = ArgumentCaptor.forClass(String.class);

    @Test
    void bareCommandRepliesWithHelp() {
        assertThat(entry().handle(c2c("/todo"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(captured()).contains("add").contains("list").contains("done");
    }

    @Test
    void addThenListKeepsInsertionOrder() {
        assertThat(entry().handle(c2c("/todo add 买菜 和 牛奶"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(captured()).isEqualTo("已添加 #1 买菜 和 牛奶");

        entry().handle(c2c("/todo add 写周报"));
        assertThat(entry().handle(c2c("/todo list"))).isEqualTo(HandlerResult.CONSUMED);

        assertThat(captured()).isEqualTo("待办:\n1. 买菜 和 牛奶\n2. 写周报");
    }

    @Test
    void doneRemovesItemAndRejectsBadIndex() {
        entry().handle(c2c("/todo add 买菜"));
        entry().handle(c2c("/todo add 写周报"));

        assertThat(entry().handle(c2c("/todo done 1"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(captured()).isEqualTo("已完成 #1 买菜");

        entry().handle(c2c("/todo done 9"));
        assertThat(captured()).contains("用法");

        entry().handle(c2c("/todo list"));
        assertThat(captured()).isEqualTo("待办:\n1. 写周报");
    }

    @Test
    void addWithoutContentOrUnknownSubcommandExplainsUsage() {
        entry().handle(c2c("/todo add"));
        assertThat(captured()).contains("用法");

        assertThat(entry().handle(c2c("/todo nope"))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(captured()).contains("未知子命令").contains("nope");
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

        assertThat(captured()).isEqualTo("已添加 #1 空格");
    }

    @SuppressWarnings("unchecked")
    private BotEventHandler<C2cMessageCreateEvent> entry() {
        return (BotEventHandler<C2cMessageCreateEvent>) feature.messageHandlers().stream()
                .filter(handler -> handler.eventType().equals(C2cMessageCreateEvent.class))
                .findFirst()
                .orElseThrow();
    }

    /** 最后一次 {@code replies.text(...)} 的内容（目标与被动标记由 BotReplies 负责）。 */
    private String captured() {
        verify(replies, atLeastOnce()).text(repliedEvent.capture(), repliedText.capture());
        return repliedText.getValue();
    }

    private static C2cMessageCreateEvent c2c(String content) {
        MessageAuthor author = new MessageAuthor("id", "nick", false, null, null, null, "USER_OPENID", null, null);
        C2cMessage message = new C2cMessage("MSG_1", author, content, "2026-10-02T00:00:00+08:00", 0, null, null, null,
                null);
        return new C2cMessageCreateEvent(1, "C2C_MESSAGE_CREATE:EVENT_ID", new JsonMapper().readTree("{}"), message);
    }
}
