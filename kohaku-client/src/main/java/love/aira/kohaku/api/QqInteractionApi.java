package love.aira.kohaku.api;

import java.util.Map;

/**
 * 互动事件响应接口：{@code PUT /interactions/{interaction_id}}。
 *
 * <p>收到 {@code INTERACTION_CREATE} 后**必须回应**（仅 {@code type=11 消息按钮}、{@code type=12 快捷菜单} 需要），
 * 否则客户端会一直 loading 直到超时 —— 这一步与"被动回复一条消息"是两件事，缺一不可。
 *
 * <p>路径参数取事件的 {@code d.id}（**不带** {@code INTERACTION_CREATE:} 前缀），与被动回复消息用的
 * {@code event_id}（最外层 id，**带**前缀）不同；同一 {@code interaction_id} 只能回应一次。
 */
public class QqInteractionApi {

    /** 回调结果：成功。 */
    public static final int CODE_SUCCESS = 0;
    /** 回调结果：操作失败。 */
    public static final int CODE_FAILED = 1;
    /** 回调结果：操作频繁。 */
    public static final int CODE_TOO_FREQUENT = 2;
    /** 回调结果：重复操作。 */
    public static final int CODE_DUPLICATE = 3;
    /** 回调结果：没有权限。 */
    public static final int CODE_NO_PERMISSION = 4;
    /** 回调结果：仅管理员操作。 */
    public static final int CODE_ADMIN_ONLY = 5;

    private final QqOpenApiClient api;

    public QqInteractionApi(QqOpenApiClient api) {
        this.api = api;
    }

    /** 回应一次互动事件；{@code code} 见本类的 {@code CODE_*} 常量。 */
    public void respond(String interactionId, int code) {
        api.put("/interactions/" + PathSegments.encode("interaction_id", interactionId), Map.of("code", code));
    }
}
