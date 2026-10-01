package love.aira.kohaku.gateway.event.model;

import java.util.List;
import love.aira.kohaku.gateway.event.model.MessageEmbedThumbnail;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：MessageEmbed（字段与官方文档一致）
 *
 * @param title 标题
 * @param prompt 消息弹窗内容
 * @param thumbnail 缩略图
 * @param fields embed 字段
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageEmbed(
        String title,
        String prompt,
        MessageEmbedThumbnail thumbnail,
        List<MessageEmbedField> fields) {
}
