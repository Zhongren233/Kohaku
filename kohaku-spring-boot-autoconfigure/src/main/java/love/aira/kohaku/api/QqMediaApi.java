package love.aira.kohaku.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import love.aira.kohaku.api.model.FileUploadRequest;
import love.aira.kohaku.api.model.FileUploadResponse;
import love.aira.kohaku.api.model.UploadPartFinishRequest;
import love.aira.kohaku.api.model.UploadPrepareRequest;
import love.aira.kohaku.api.model.UploadPrepareResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 富媒体（图片/视频/语音/文件）接口。
 *
 * <ul>
 *   <li>{@code POST /v2/users/{user_openid}/files}、{@code POST /v2/groups/{group_openid}/files}
 *       —— URL 上传与分片合并，返回 {@code file_info}</li>
 *   <li>{@code POST /v2/users/{user_id}/upload_prepare}、{@code POST /v2/groups/{group_id}/upload_prepare}
 *       —— 申请分片上传，返回 upload_id 与各分片预签名 URL</li>
 *   <li>{@code POST /v2/users/{user_id}/upload_part_finish}、{@code POST /v2/groups/{group_id}/upload_part_finish}
 *       —— 分片 PUT 完成后登记</li>
 * </ul>
 *
 * <p>文档中 upload_prepare/upload_part_finish 的路径参数名写作 {@code user_id}/{@code group_id}，
 * files 写作 {@code user_openid}/{@code group_openid}，含义都是 OpenID，这里统一按 OpenID 处理。
 * 单聊与群聊上传的文件不互通，上传归属由 {@link Scope} 决定。
 */
@Component
public class QqMediaApi {

    private static final Logger log = LoggerFactory.getLogger(QqMediaApi.class);

    /** 上传归属；两端上传的文件不可混用。 */
    public enum Scope {
        /** 单聊：/v2/users/{openid}/... */
        USER("users"),
        /** 群聊：/v2/groups/{openid}/... */
        GROUP("groups");

        private final String path;

        Scope(String path) {
            this.path = path;
        }

        String path() {
            return path;
        }
    }

    private final QqOpenApiClient api;
    private final HttpClient httpClient;

    public QqMediaApi(QqOpenApiClient api, HttpClient qqHttpClient) {
        this.api = api;
        this.httpClient = qqHttpClient;
    }

    /** URL 上传（传 url）或分片合并（传 upload_id），返回 file_info。 */
    public FileUploadResponse upload(Scope scope, String openid, FileUploadRequest request) {
        return api.post(scopePath(scope, openid) + "/files", request, FileUploadResponse.class);
    }

    /** 分片上传第一步：申请上传任务与分片预签名地址。 */
    public UploadPrepareResponse prepareUpload(Scope scope, String openid, UploadPrepareRequest request) {
        return api.post(scopePath(scope, openid) + "/upload_prepare", request, UploadPrepareResponse.class);
    }

    /** 分片上传第二步：分片 PUT 成功后登记该分片。 */
    public void finishUploadPart(Scope scope, String openid, UploadPartFinishRequest request) {
        api.post(scopePath(scope, openid) + "/upload_part_finish", request);
    }

    /**
     * 完整的分片上传（文档推荐流程）：预上传 → 逐片 PUT 到预签名地址 → 登记分片 → 携带 upload_id 合并。
     *
     * <p>按 {@code upload_config.retry_delay/retry_timeout} 重试失败的分片；分片按序号串行上传。
     *
     * @param srvSendMsg true 表示合并后直接发送消息（占用主动消息频次，响应中返回消息 id）
     */
    public FileUploadResponse uploadFile(Scope scope, String openid, int fileType, Path file, String fileName,
                                         boolean srvSendMsg) {
        long fileSize = size(file);
        UploadPrepareResponse prepared = prepareUpload(scope, openid, UploadPrepareRequest.of(fileType, fileSize,
                fileName, digest(file, "MD5", Long.MAX_VALUE), digest(file, "SHA-1", Long.MAX_VALUE),
                digest(file, "MD5", UploadPrepareRequest.CHECKSUM_PREFIX_BYTES)));
        log.debug("chunked upload prepared: upload_id={} parts={} block_size={}",
                prepared.uploadId(), prepared.parts() == null ? 0 : prepared.parts().size(), prepared.blockSize());

        long fallbackBlockSize = prepared.blockSizeBytes() > 0 ? prepared.blockSizeBytes() : fileSize;
        List<UploadPrepareResponse.UploadPart> parts = prepared.parts() == null ? List.of()
                : prepared.parts().stream()
                        .sorted(Comparator.comparing(UploadPrepareResponse.UploadPart::index))
                        .toList();
        long offset = 0;
        for (UploadPrepareResponse.UploadPart part : parts) {
            long blockSize = part.blockSize() == null || part.blockSize().isBlank()
                    ? fallbackBlockSize
                    : Long.parseLong(part.blockSize().trim());
            if (blockSize <= 0) {
                blockSize = fallbackBlockSize;
            }
            byte[] slice = readSlice(file, offset, blockSize);
            offset += slice.length;
            putPart(part.presignedUrl(), slice, prepared.uploadConfig());
            finishUploadPart(scope, openid,
                    UploadPartFinishRequest.of(prepared.uploadId(), part.index(), slice.length, md5(slice)));
        }
        return upload(scope, openid,
                FileUploadRequest.merge(fileType, prepared.uploadId(), fileName).sending(srvSendMsg));
    }

    private void putPart(String presignedUrl, byte[] data, UploadPrepareResponse.UploadConfig config) {
        long retryDelayMs = config == null || config.retryDelay() == null ? 1000L : config.retryDelay() * 1000L;
        long retryTimeoutMs = config == null || config.retryTimeout() == null ? 300_000L : config.retryTimeout() * 1000L;
        long deadline = System.nanoTime() + retryTimeoutMs * 1_000_000L;
        while (true) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(presignedUrl))
                        .timeout(Duration.ofMinutes(5))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                        .build();
                ApiResponses.requireSuccess(HttpCalls.send(httpClient, request));
                return;
            } catch (RuntimeException e) {
                if (System.nanoTime() >= deadline) {
                    throw e;
                }
                log.warn("uploading part failed, retrying in {} ms: {}", retryDelayMs, e.toString());
                sleep(retryDelayMs);
            }
        }
    }

    private static String scopePath(Scope scope, String openid) {
        return "/v2/" + scope.path() + "/" + PathSegments.encode("openid", openid);
    }


    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new QqApiException("cannot read file " + file + ": " + e.getMessage(), e);
        }
    }

    private static byte[] readSlice(Path file, long offset, long length) {
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(length));
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // 继续读满本分片
            }
            return Arrays.copyOf(buffer.array(), buffer.position());
        } catch (IOException e) {
            throw new QqApiException("cannot read file slice from " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * 计算文件的 MD5/SHA1；{@code limit} 用于只取前若干字节（md5_10m）。
     */
    private static String digest(Path file, String algorithm, long limit) {
        MessageDigest digest = newDigest(algorithm);
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            long remaining = limit;
            int read;
            while (remaining > 0 && (read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                digest.update(buffer, 0, read);
                remaining -= read;
            }
        } catch (IOException e) {
            throw new QqApiException("cannot read file " + file + ": " + e.getMessage(), e);
        }
        return hex(digest.digest());
    }

    private static String md5(byte[] data) {
        return hex(newDigest("MD5").digest(data));
    }

    private static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QqApiException("interrupted while retrying upload", e);
        }
    }
}
