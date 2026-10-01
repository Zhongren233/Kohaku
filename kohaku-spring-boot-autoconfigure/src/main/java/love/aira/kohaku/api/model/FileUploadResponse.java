package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 富媒体上传响应。
 *
 * @param fileUuid 文件唯一 ID
 * @param fileInfo 用于发送消息的 {@code media.file_info}，内部为序列化数据，直接透传
 * @param ttl      file_info 有效期（秒），0 表示长期有效，过期需重新上传
 * @param id       消息 ID，仅 {@code srv_send_msg=true} 时返回
 * @param rawUrl   文件下载链接，仅分片合并且 file_type 为图片/视频/语音时返回
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileUploadResponse(String fileUuid, String fileInfo, Integer ttl, String id, String rawUrl) {
}
