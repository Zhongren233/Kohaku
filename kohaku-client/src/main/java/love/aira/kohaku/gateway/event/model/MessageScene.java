package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 消息场景上下文（文档 MessageScene）。{@code ext} 是 {@code key=value} 形式的字符串数组。
 *
 * <p>平台为保证可达性可能重复推送同一 {@code msg_id}，文档要求结合 {@code message_scene.ext}
 * 中的 {@code msg_idx} 去重；引用消息场景还会带 {@code ref_msg_idx}。
 *
 * @param source 场景来源：default=默认聊天窗口
 * @param ext    扩展数据列表，形如 {@code ["msg_idx=REFIDX_xxx==", "ref_msg_idx=REFIDX_yyy=="]}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageScene(String source, List<String> ext) {

    /** 本消息的索引（{@code msg_idx}），不存在时返回 null。 */
    public String messageIndex() {
        return value("msg_idx");
    }

    /** 被引用消息的索引（{@code ref_msg_idx}），不存在时返回 null。 */
    public String refMessageIndex() {
        return value("ref_msg_idx");
    }

    /** 鉴权令牌（{@code auth_token}），不存在时返回 null。 */
    public String authToken() {
        return value("auth_token");
    }

    /** 从 {@code ext} 中按 key 取 {@code key=value} 的 value。 */
    public String value(String key) {
        if (ext == null) {
            return null;
        }
        String prefix = key + "=";
        return ext.stream()
                .filter(entry -> entry != null && entry.startsWith(prefix))
                .map(entry -> entry.substring(prefix.length()))
                .findFirst()
                .orElse(null);
    }
}
