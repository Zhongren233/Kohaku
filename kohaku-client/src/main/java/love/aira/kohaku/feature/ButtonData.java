package love.aira.kohaku.feature;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 按钮回调数据的编解码（方案 A：自描述，状态直接放在按钮 data 里，机器人侧无需保存会话）。
 *
 * <p>格式：{@code featureId:action[:k=v;k=v]}，其中键值按 URL 编码，因此可安全携带中文与特殊字符。
 * 例：{@code card:page:p=3}、{@code card:next:p=2;q=%E6%B5%8B%E8%AF%95}。
 *
 * <p>注意：平台对按钮 data 的长度上限未在文档中给出，状态应保持精简；超出时请改用"token + 服务端会话表"方案。
 */
public final class ButtonData {

    private static final char SEGMENT = ':';
    private static final char PAIR = ';';
    private static final char EQUALS = '=';
    private static final Pattern SIMPLE = Pattern.compile("[A-Za-z0-9_.-]+");

    private ButtonData() {
    }

    /** 解析结果：功能名、动作名与状态键值对。 */
    public record Parts(String featureId, String action, Map<String, String> state) {

        /** 读取整型状态，缺失或非法时返回默认值。 */
        public int intState(String key, int defaultValue) {
            String value = state.get(key);
            if (value == null) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    /** 编码按钮 data；{@code featureId}/{@code action} 只允许字母数字与 {@code _. -}。 */
    public static String encode(String featureId, String action, Map<String, String> state) {
        requireSimple("featureId", featureId);
        requireSimple("action", action);
        StringBuilder data = new StringBuilder(featureId).append(SEGMENT).append(action);
        if (state != null && !state.isEmpty()) {
            data.append(SEGMENT);
            boolean first = true;
            for (Map.Entry<String, String> entry : state.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                if (!first) {
                    data.append(PAIR);
                }
                data.append(encodeValue(entry.getKey())).append(EQUALS).append(encodeValue(entry.getValue()));
                first = false;
            }
        }
        return data.toString();
    }

    /** 解码按钮 data；格式非法（缺少功能名/动作名）时返回 {@code null}，调用方按 IGNORED 处理。 */
    public static Parts decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        String[] segments = data.split(String.valueOf(SEGMENT), 3);
        if (segments.length < 2 || segments[0].isBlank() || segments[1].isBlank() || !SIMPLE.matcher(segments[0]).matches()
                || !SIMPLE.matcher(segments[1]).matches()) {
            return null;
        }
        Map<String, String> state = new LinkedHashMap<>();
        if (segments.length == 3 && !segments[2].isBlank()) {
            for (String pair : segments[2].split(String.valueOf(PAIR))) {
                int index = pair.indexOf(EQUALS);
                if (index <= 0) {
                    continue;
                }
                state.put(decodeValue(pair.substring(0, index)), decodeValue(pair.substring(index + 1)));
            }
        }
        return new Parts(segments[0], segments[1], Map.copyOf(state));
    }

    /** 校验功能名/动作名是否合法（字母数字与 {@code _ . -}）；构建期调用可及早失败。 */
    public static void requireSimple(String name, String value) {
        if (value == null || !SIMPLE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 只能包含字母数字与 _ . -，实际为: " + value);
        }
    }

    private static String encodeValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decodeValue(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
