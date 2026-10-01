package love.aira.kohaku.gateway.event.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：MessageArk（字段与官方文档一致）
 *
 * @param templateId ark 模板 id（需申请）
 * @param kv kv 值列表
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageArk(
        Integer templateId,
        List<MessageArkKv> kv) {
}
