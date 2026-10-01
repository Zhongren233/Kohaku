package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 分片上传完成登记请求（{@code /v2/{scope}/{openid}/upload_part_finish}）：
 * 分片 PUT 到预签名 URL 成功后调用，通知服务端该分片已就绪。
 *
 * @param blockSize 分块大小（字节），文档类型为 string
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadPartFinishRequest(String uploadId, Integer partIndex, String blockSize, String md5) {

    public static UploadPartFinishRequest of(String uploadId, int partIndex, long blockSize, String md5) {
        return new UploadPartFinishRequest(uploadId, partIndex, String.valueOf(blockSize), md5);
    }
}
