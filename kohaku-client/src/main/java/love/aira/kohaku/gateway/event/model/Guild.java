package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：Guild（字段与官方文档一致）
 *
 * @param id 频道 ID
 * @param name 频道名称
 * @param icon 频道头像 URL
 * @param ownerId 频道创建者 ID
 * @param memberCount 频道成员数
 * @param maxMembers 频道成员上限
 * @param description 频道简介
 * @param joinedAt 加入时间，ISO8601
 * @param opUserId 操作人 ID
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Guild(
        String id,
        String name,
        String icon,
        String ownerId,
        Integer memberCount,
        Integer maxMembers,
        String description,
        String joinedAt,
        String opUserId) {
}
