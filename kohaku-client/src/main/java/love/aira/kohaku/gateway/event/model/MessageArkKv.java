package love.aira.kohaku.gateway.event.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：MessageArkKv（字段与官方文档一致）
 *
 * @param key key
 * @param value value
 * @param obj obj 列表
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageArkKv(
        String key,
        String value,
        List<MessageArkObj> obj) {
}
