package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Markdown 消息（msg_type=2）。填写后 {@code content} 必须为空。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarkdownMessage(Integer templateId, String content, String customTemplateId,
                              Boolean forceVerifyImageResource) {

    /** 原生 Markdown 内容（单聊/群聊已开放自定义 Markdown）。 */
    public static MarkdownMessage of(String content) {
        return new MarkdownMessage(null, content, null, null);
    }

    /** 平台 Markdown 模板（频道场景需内邀开通）。 */
    public static MarkdownMessage template(int templateId) {
        return new MarkdownMessage(templateId, null, null, null);
    }

    /** 是否校验图片转存结果，为 true 时转存失败会直接返回错误且不发送。 */
    public MarkdownMessage verifyImageResource(boolean verify) {
        return new MarkdownMessage(templateId, content, customTemplateId, verify);
    }
}
