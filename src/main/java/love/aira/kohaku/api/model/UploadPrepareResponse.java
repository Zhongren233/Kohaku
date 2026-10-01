package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 分片上传预上传响应：客户端按 {@code block_size} 切分文件，逐片 PUT 到对应 {@code presigned_url}，
 * 再调用 upload_part_finish 登记，最后携带 {@code upload_id} 调 /files 合并。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadPrepareResponse(String uploadId, String blockSize, List<UploadPart> parts,
                                    UploadConfig uploadConfig) {

    /** 单个分片：PUT 到 {@code presignedUrl} 的字节区间为 {@code [index * blockSize, +blockSize)}。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadPart(Integer index, String presignedUrl, String blockSize) {
    }

    /** 上传行为控制参数，由后台下发。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UploadConfig(Integer concurrency, Integer retryTimeout, Integer retryDelay) {
    }

    /** 解析后的分块大小（字节）。 */
    public long blockSizeBytes() {
        return blockSize == null ? 0 : Long.parseLong(blockSize.trim());
    }
}
