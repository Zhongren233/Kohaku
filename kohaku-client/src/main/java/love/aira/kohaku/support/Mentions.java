package love.aira.kohaku.support;

import java.util.List;
import love.aira.kohaku.gateway.event.model.MessageAuthor;

/**
 * 群消息行首 @ 提及的剥离。
 *
 * <p>平台的两种群消息事件对 @ 的处理**不一致**（2026-10-02 真机实测）：
 * <ul>
 *   <li>{@code GROUP_AT_MESSAGE_CREATE}（群内 @机器人）：机器人自己的提及已被平台剥掉，
 *       content 形如 {@code " /card"}，且不下发 {@code mentions}；</li>
 *   <li>{@code GROUP_MESSAGE_CREATE}（群全量消息）：content 原样保留，形如 {@code "<@1D0478…> /card"}，
 *       并在 {@code mentions} 里列出被 @ 的人（**含机器人自身**，其 {@code bot=true}）。</li>
 * </ul>
 *
 * <p>所以命令匹配前需要先剥掉行首提及，本类只剥**行首**、且只剥**机器人**的提及
 * （{@code mentions} 中 {@code bot=true} 且 id 匹配的项；没有任何可比对的信息时，
 * 按平台语法 {@code <@id>} / {@code <@!id>} 直接剥）。这样 {@code @Alice /card} 不会被误当成命令。
 */
public final class Mentions {

    private Mentions() {
    }

    /**
     * 去掉行首的机器人 @ 提及以及其后的空白。
     *
     * @param content  原始消息文本（可为 {@code null}）
     * @param mentions 事件体里的提及列表（可为 {@code null}/空，此时按平台语法尽力剥离）
     * @return 剥离后的文本；没有可剥的内容时**原样返回**（不做 trim，避免改变非命令语义）
     */
    public static String stripLeading(String content, List<MessageAuthor> mentions) {
        if (content == null) {
            return null;
        }
        int cursor = 0;
        boolean stripped = false;
        while (cursor < content.length()) {
            int tokenStart = cursor;
            while (tokenStart < content.length() && Character.isWhitespace(content.charAt(tokenStart))) {
                tokenStart++;
            }
            if (tokenStart + 2 > content.length() || content.charAt(tokenStart) != '<'
                    || content.charAt(tokenStart + 1) != '@') {
                break;
            }
            int tokenEnd = content.indexOf('>', tokenStart + 2);
            if (tokenEnd < 0) {
                break;
            }
            String id = content.substring(tokenStart + 2, tokenEnd);
            if (id.startsWith("!")) {                       // <@!id>（昵称式提及）
                id = id.substring(1);
            }
            if (!isBotMention(id, mentions)) {
                break;
            }
            stripped = true;
            cursor = tokenEnd + 1;
        }
        if (!stripped) {
            return content;
        }
        while (cursor < content.length() && Character.isWhitespace(content.charAt(cursor))) {
            cursor++;
        }
        return content.substring(cursor);
    }

    private static boolean isBotMention(String id, List<MessageAuthor> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return true;                                    // 无从比对：平台语法 <@id> 只用于提及
        }
        for (MessageAuthor mention : mentions) {
            if (Boolean.TRUE.equals(mention.bot()) && matches(id, mention)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String id, MessageAuthor mention) {
        return id.equals(mention.id()) || id.equals(mention.memberOpenid()) || id.equals(mention.userOpenid());
    }
}
