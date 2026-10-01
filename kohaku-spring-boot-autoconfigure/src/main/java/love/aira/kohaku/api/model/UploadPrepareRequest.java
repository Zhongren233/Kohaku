package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 分片上传预上传请求（{@code /v2/{scope}/{openid}/upload_prepare}）。
 *
 * @param fileSize 文件大小（字节），文档类型为 string
 * @param md5TenMb 文件前 {@value #CHECKSUM_PREFIX_BYTES} 字节的 MD5
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadPrepareRequest(Integer fileType, String fileSize, String fileName, String md5, String sha1,
                                   @JsonProperty("md5_10m") String md5TenMb) {

    /**
     * 文档规定取文件前 10002432 字节（约 10MB）计算 md5_10m；若文件更小则取全文件。
     */
    public static final long CHECKSUM_PREFIX_BYTES = 10_002_432L;

    public static UploadPrepareRequest of(int fileType, long fileSize, String fileName, String md5, String sha1,
                                          String md5TenMb) {
        return new UploadPrepareRequest(fileType, String.valueOf(fileSize), fileName, md5, sha1, md5TenMb);
    }
}
