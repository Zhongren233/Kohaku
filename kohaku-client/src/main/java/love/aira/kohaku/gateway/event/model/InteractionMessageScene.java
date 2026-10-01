package love.aira.kohaku.gateway.event.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：InteractionMessageScene（字段与官方文档一致）
 *
 * @param ext 扩展键值对列表
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InteractionMessageScene(
        List<String> ext) {
}
