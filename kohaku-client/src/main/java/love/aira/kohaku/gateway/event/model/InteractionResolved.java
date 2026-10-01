package love.aira.kohaku.gateway.event.model;

import love.aira.kohaku.gateway.event.model.AuthorizeData;
import love.aira.kohaku.gateway.event.model.InteractionMessageScene;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 事件体/嵌套对象：InteractionResolved（字段与官方文档一致）
 *
 * @param buttonData 按钮 data（发送按钮时设置）
 * @param buttonId 按钮 id
 * @param userId 操作用户 id（仅频道）
 * @param featureId 功能 id（仅快捷菜单）
 * @param messageId 操作的消息 id
 * @param feedbackOpt 反馈选项：LIKE/UNLIKE
 * @param checked 反馈是否选中
 * @param action 操作类型（故事集/切换模型）
 * @param messageScene 消息场景（仅消息反馈）
 * @param authorizeData 授权数据（仅授权事件）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InteractionResolved(
        String buttonData,
        String buttonId,
        String userId,
        String featureId,
        String messageId,
        String feedbackOpt,
        Integer checked,
        String action,
        InteractionMessageScene messageScene,
        AuthorizeData authorizeData) {
}
