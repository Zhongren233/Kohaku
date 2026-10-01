package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：Channel（字段与官方文档一致）
 *
 * @param id 子频道 ID
 * @param guildId 所属频道 ID
 * @param name 子频道名称
 * @param type 子频道类型：0=文字 2=语音 4=分组 10005=直播 10006=应用 10007=论坛
 * @param subType 子频道子类型
 * @param ownerId 创建者 ID
 * @param opUserId 操作人 ID
 * @param position 排序位置
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Channel(
        String id,
        String guildId,
        String name,
        Integer type,
        Integer subType,
        String ownerId,
        String opUserId,
        Integer position) {
}
