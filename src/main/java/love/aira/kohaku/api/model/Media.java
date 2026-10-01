package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 富媒体消息体（msg_type=7），{@code file_info} 来自文件上传接口。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Media(String fileInfo) {

    public static Media of(String fileInfo) {
        return new Media(fileInfo);
    }
}
