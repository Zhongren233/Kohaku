package love.aira.kohaku.interaction;

import java.util.Map;
import love.aira.kohaku.api.model.Keyboard;

/**
 * 键盘构造助手（协议级）：把功能名/动作/状态编码进按钮 data，业务侧不用手写 data 字符串。
 *
 * <p>这里只提供与平台协议直接对应的两种按钮；具体业务形态的键盘（分页、向导、菜单…）
 * 由业务/示例自行组合，例如 kohaku-example 中的 `CardKeyboards`。
 */
public final class FeatureKeyboards {

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
}
