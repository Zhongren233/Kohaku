package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 富媒体上传/合并请求体（单聊 {@code /v2/users/{openid}/files}、群聊 {@code /v2/groups/{openid}/files}）。
 *
 * <p>两种用法：URL 上传传 {@code url}（平台下载转存）；分片上传传 {@code upload_id} 触发合并，此时 url 可为空。
 *
 * @param fileType   1=图片(png/jpg) 2=视频(mp4) 3=语音(silk) 4=文件；超过软限制会降级为文件，超过硬限制报错
 * @param srvSendMsg true=上传后直接发送并占用主动消息频次，响应中返回消息 id
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileUploadRequest(Integer fileType, String url, Boolean srvSendMsg, String fileName, String uploadId) {

    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_AUDIO = 3;
    public static final int TYPE_FILE = 4;

    /** URL 上传：仅取回 file_info，供后续发送消息使用。 */
    public static FileUploadRequest byUrl(int fileType, String url) {
        return new FileUploadRequest(fileType, url, false, null, null);
    }

    public static FileUploadRequest byUrl(int fileType, String url, String fileName) {
        return new FileUploadRequest(fileType, url, false, fileName, null);
    }

    /** 分片上传合并：分片全部完成后携带 upload_id 调用，取回 file_info。 */
    public static FileUploadRequest merge(int fileType, String uploadId, String fileName) {
        return new FileUploadRequest(fileType, null, false, fileName, uploadId);
    }

    public FileUploadRequest sending(boolean srvSendMsg) {
        return new FileUploadRequest(fileType, url, srvSendMsg, fileName, uploadId);
    }
}
