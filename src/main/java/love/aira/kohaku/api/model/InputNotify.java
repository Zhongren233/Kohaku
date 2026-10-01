package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 输入中状态（msg_type=6，仅单聊）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InputNotify(Integer inputType, Integer inputSecond) {

    /** 文档固定取值。 */
    public static final int TYPE_INPUTTING = 1;

    /** 状态持续时间，最长 60 秒。 */
    public static InputNotify typing(int seconds) {
        if (seconds < 1 || seconds > 60) {
            throw new IllegalArgumentException("input_second must be in [1, 60], was " + seconds);
        }
        return new InputNotify(TYPE_INPUTTING, seconds);
    }
}
