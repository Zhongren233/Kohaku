package love.aira.kohaku.gateway.event.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：MessageArkObj（字段与官方文档一致）
 *
 * @param objKv objkv 列表
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageArkObj(
        List<MessageArkObjKv> objKv) {
}
