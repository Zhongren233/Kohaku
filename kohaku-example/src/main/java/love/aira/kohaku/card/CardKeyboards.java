package love.aira.kohaku.card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.feature.FeatureKeyboards;
import love.aira.kohaku.support.Pagination;

/**
 * 分页键盘（业务级，放在示例而非客户端库）：上一页 / 页码 / 下一页。
 *
 * <p>按钮携带的是**目标页码**（状态键 {@code p}），因此重复点击或平台重放都是幂等的。
 * 回调按钮与指令按钮两种形态由 {@link CardFeature} 按是否开通互动权限选择。
 */
final class CardKeyboards {

    static final String ACTION_PREV = "prev";
    static final String ACTION_NEXT = "next";
    static final String ACTION_PAGE = "page";
    /** 状态里表示页码的键。 */
    static final String STATE_PAGE = "p";

    private CardKeyboards() {
    }

    /** 回调按钮版翻页行：data 形如 {@code card:next:p=2}。 */
    static Keyboard callbackPagination(String featureId, Pagination pagination, Map<String, String> state) {
        List<Keyboard.Button> buttons = new ArrayList<>(3);
        if (pagination.hasPrevious()) {
            buttons.add(FeatureKeyboards.button("prev", featureId, ACTION_PREV, "上一页",
                    pageState(state, pagination.page() - 1)));
        }
        buttons.add(FeatureKeyboards.button("page", featureId, ACTION_PAGE, pagination.label(),
                pageState(state, pagination.page())));
        if (pagination.hasNext()) {
            buttons.add(FeatureKeyboards.button("next", featureId, ACTION_NEXT, "下一页",
                    pageState(state, pagination.page() + 1)));
        }
        return Keyboard.of(Keyboard.Row.of(buttons.toArray(Keyboard.Button[]::new)));
    }

    /** 指令按钮版翻页行：data 形如 {@code /card next 2}，点击后作为普通消息回到命令入口。 */
    static Keyboard commandPagination(String featureId, Pagination pagination) {
        List<Keyboard.Button> buttons = new ArrayList<>(3);
        if (pagination.hasPrevious()) {
            buttons.add(FeatureKeyboards.commandButton("prev", "上一页",
                    command(featureId, ACTION_PREV, pagination.page() - 1)));
        }
        buttons.add(FeatureKeyboards.commandButton("page", pagination.label(),
                command(featureId, ACTION_PAGE, pagination.page())));
        if (pagination.hasNext()) {
            buttons.add(FeatureKeyboards.commandButton("next", "下一页",
                    command(featureId, ACTION_NEXT, pagination.page() + 1)));
        }
        return Keyboard.of(Keyboard.Row.of(buttons.toArray(Keyboard.Button[]::new)));
    }

    static String command(String featureId, String action, int page) {
        return "/" + featureId + " " + action + " " + page;
    }

    /** 复制状态并写入目标页码。 */
    static Map<String, String> pageState(Map<String, String> base, int page) {
        Map<String, String> state = new LinkedHashMap<>(base == null ? Map.of() : base);
        state.put(STATE_PAGE, Integer.toString(page));
        return state;
    }
}
