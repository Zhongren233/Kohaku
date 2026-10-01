package love.aira.kohaku.gateway.event.model;

import love.aira.kohaku.gateway.event.model.InteractionResolved;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：InteractionData（字段与官方文档一致）
 *
 * @param type 互动数据类型
 * @param resolved 解析后的互动数据
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InteractionData(
        Integer type,
        InteractionResolved resolved) {
}
