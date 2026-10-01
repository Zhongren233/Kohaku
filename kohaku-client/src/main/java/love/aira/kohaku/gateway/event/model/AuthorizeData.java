package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：AuthorizeData（字段与官方文档一致）
 *
 * @param optScene 授权操作场景：setting=资料页 dialog=弹窗
 * @param scope 授权范围：c2c_push / group_push
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizeData(
        String optScene,
        String scope) {
}
