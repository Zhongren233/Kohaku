package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 内嵌键盘：短形式只传平台模板 {@link #template(String)}，长形式传自定义布局 {@link #of(Row...)}。
 *
 * <p><b>实测约束</b>：客户端只在 <b>markdown 消息（{@code msg_type=2}）</b> 上渲染自定义键盘；
 * 纯文本消息（{@code msg_type=0}）带键盘会被平台静默丢弃（文档未写明），因此发送带键盘的消息请用
 * {@code SendMessageRequest.markdown(...)}。{@code QqMessageApi} 会在检测到该误用时打 WARN。
 *
 * <p>子结构按文档 {@code keyboard.content.rows[].buttons[]} 建模。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Keyboard(String id, Content content) {

    public static Keyboard template(String id) {
        return new Keyboard(id, null);
    }

    public static Keyboard of(Row... rows) {
        return new Keyboard(null, new Content(List.of(rows)));
    }

    public static Keyboard of(List<Row> rows) {
        return new Keyboard(null, new Content(rows));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(List<Row> rows) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(List<Button> buttons) {

        public static Row of(Button... buttons) {
            return new Row(List.of(buttons));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Button(String id, RenderData renderData, Action action, String groupId) {

        /** 跳转按钮（action.type=0）。 */
        public static Button link(String id, String label, String url) {
            return new Button(id, RenderData.of(label),
                    new Action(Action.TYPE_LINK, null, url, null, null, null, null, null, null), null);
        }

        /** 回调按钮（action.type=1）：点击后把 data 回调给机器人。 */
        public static Button callback(String id, String label, String data) {
            return new Button(id, RenderData.of(label),
                    new Action(Action.TYPE_CALLBACK, Permission.everyone(), data, null, null, null, null, null, null),
                    null);
        }

        /** 指令按钮（action.type=2）：点击后自动发送 {@code data}（通常形如 {@code /指令}）。 */
        public static Button command(String id, String label, String data) {
            return new Button(id, RenderData.of(label),
                    new Action(Action.TYPE_COMMAND, Permission.everyone(), data, null, null, null, null, null, null),
                    null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RenderData(String label, String visitedLabel, Integer style) {

        public static final int STYLE_GREY = 0;
        public static final int STYLE_BLUE = 1;
        public static final int STYLE_WHITE_RED_TEXT = 3;
        public static final int STYLE_BLUE_WHITE_TEXT = 4;

        /** 仅设置按钮文字，其余交给平台默认（不传则点击后文字保持不变）。 */
        public static RenderData of(String label) {
            return new RenderData(label, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Action(Integer type, Permission permission, String data, Integer clickLimit,
                         String unsupportTips, Boolean enter, Boolean reply, Integer anchor, Modal modal) {

        public static final int TYPE_LINK = 0;
        public static final int TYPE_CALLBACK = 1;
        public static final int TYPE_COMMAND = 2;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Permission(Integer type, List<String> specifyUserIds, List<String> specifyRoleIds) {

        public static final int TYPE_SPECIFIED_USERS = 0;
        public static final int TYPE_ADMINISTRATORS = 1;
        public static final int TYPE_EVERYONE = 2;

        public static Permission everyone() {
            return new Permission(TYPE_EVERYONE, null, null);
        }

        public static Permission users(List<String> userIds) {
            return new Permission(TYPE_SPECIFIED_USERS, userIds, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Modal(String content, String confirmText, String cancelText) {
    }
}
