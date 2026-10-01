package love.aira.kohaku.feature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.model.Keyboard;

/**
 * 功能按钮键盘助手：把功能名/动作/状态编码进按钮 data，业务侧不用手写 data 字符串。
 *
 * <p>按钮 id 只在同一键盘内需要唯一：翻页行使用固定的 {@code prev}/{@code page}/{@code next}，
 * 且都是独立按钮（不要放进同一个 {@code group_id}，否则点一个会让其余一起变灰）。
 */
public final class FeatureKeyboards {

    public static final String ACTION_PREV = "prev";
    public static final String ACTION_NEXT = "next";
    public static final String ACTION_PAGE = "page";
    /** 状态里表示页码的键。 */
    public static final String STATE_PAGE = "p";

    private FeatureKeyboards() {
    }

    /** 生成一个回调按钮：点击后由 {@link InteractionRouter} 投递给 {@code featureId} 的 {@code action}。 */
    public static Keyboard.Button button(String buttonId, String featureId, String action, String label,
                                         Map<String, String> state) {
        return Keyboard.Button.callback(buttonId, label, ButtonData.encode(featureId, action, state));
    }

    /**
     * 生成一个**指令按钮**：点击后客户端把 {@code command} 当作一条普通消息发出，机器人侧按命令解析。
     *
     * <p>适用于未开通 {@code INTERACTION} 权限（收不到互动事件）的机器人：整条链路只依赖消息事件。
     */
    public static Keyboard.Button commandButton(String buttonId, String label, String command) {
        return Keyboard.Button.command(buttonId, label, command);
    }

    /** 翻页行：上一页 / 页码（点击按当前状态重渲染） / 下一页。 */
    public static Keyboard pagination(String featureId, Pagination pagination, Map<String, String> state) {
        List<Keyboard.Button> buttons = new ArrayList<>(3);
        if (pagination.hasPrevious()) {
            buttons.add(button("prev", featureId, ACTION_PREV, "上一页", pageState(state, pagination.page() - 1)));
        }
        buttons.add(button("page", featureId, ACTION_PAGE, pagination.label(), pageState(state, pagination.page())));
        if (pagination.hasNext()) {
            buttons.add(button("next", featureId, ACTION_NEXT, "下一页", pageState(state, pagination.page() + 1)));
        }
        return Keyboard.of(Keyboard.Row.of(buttons.toArray(Keyboard.Button[]::new)));
    }

    /** 复制状态并写入页码，供翻页按钮的 data 使用。 */
    public static Map<String, String> pageState(Map<String, String> base, int page) {
        Map<String, String> state = new LinkedHashMap<>(base == null ? Map.of() : base);
        state.put(STATE_PAGE, Integer.toString(page));
        return state;
    }
}
