package love.aira.kohaku.gateway.event.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：Member（字段与官方文档一致）
 *
 * @param user 用户的频道基础信息
 * @param nick 昵称
 * @param roles 频道内身份组 ID 列表
 * @param joinedAt 加入频道时间，ISO8601
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Member(
        MessageAuthor user,
        String nick,
        List<String> roles,
        String joinedAt) {
}
