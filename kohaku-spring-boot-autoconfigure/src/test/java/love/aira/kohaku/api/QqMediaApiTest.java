package love.aira.kohaku.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import love.aira.kohaku.api.model.FileUploadRequest;
import love.aira.kohaku.api.model.FileUploadResponse;
import love.aira.kohaku.api.model.UploadPartFinishRequest;
import love.aira.kohaku.api.model.UploadPrepareRequest;
import love.aira.kohaku.api.model.UploadPrepareResponse;
import love.aira.kohaku.config.QqBotProperties;
import love.aira.kohaku.config.QqIntent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class QqMediaApiTest {

    private FakeQqApiServer server;
    private QqMediaApi api;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        server = new FakeQqApiServer();
        mapper = new JsonMapper();
        QqBotProperties properties = new QqBotProperties("app-id", "app-secret", server.baseUrl(),
                server.baseUrl() + "/app/getAppAccessToken", List.of(QqIntent.PUBLIC_GUILD_MESSAGES), 0, 1,
                "kohaku", true, Duration.ofSeconds(1), Duration.ofSeconds(60), true, false);
        server.stub("POST /app/getAppAccessToken", 200,
                "{\"access_token\":\"test-token\",\"expires_in\":\"7200\"}");
        HttpClient httpClient = HttpClient.newHttpClient();
        AccessTokenProvider tokens = new AccessTokenProvider(httpClient, mapper, properties);
        api = new QqMediaApi(new QqOpenApiClient(httpClient, mapper, properties, tokens), httpClient);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void uploadsImageByUrl() {
        server.stub("POST /v2/users/OPENID_USER/files", 200,
                "{\"file_uuid\":\"uuid_1\",\"file_info\":\"FILE_INFO_1\",\"ttl\":300}");

        FileUploadResponse response = api.upload(QqMediaApi.Scope.USER, "OPENID_USER",
                FileUploadRequest.byUrl(FileUploadRequest.TYPE_IMAGE, "https://example.com/a.png", "a.png"));

        assertThat(response.fileUuid()).isEqualTo("uuid_1");
        assertThat(response.fileInfo()).isEqualTo("FILE_INFO_1");
        assertThat(response.ttl()).isEqualTo(300);
        assertThat(mapper.readTree(server.calls("POST /v2/users/OPENID_USER/files").getFirst().body()))
                .isEqualTo(mapper.readTree("""
                        {"file_type":1,"url":"https://example.com/a.png","srv_send_msg":false,"file_name":"a.png"}"""));
    }

    @Test
    void preparesChunkedUploadForGroup() {
        server.stub("POST /v2/groups/GROUP_OPENID/upload_prepare", 200, """
                {"upload_id":"u1","block_size":"10485760",
                 "parts":[{"index":0,"presigned_url":"https://cos/part0","block_size":"10485760"}],
                 "upload_config":{"concurrency":1,"retry_timeout":300,"retry_delay":1}}""");

        UploadPrepareResponse response = api.prepareUpload(QqMediaApi.Scope.GROUP, "GROUP_OPENID",
                UploadPrepareRequest.of(FileUploadRequest.TYPE_FILE, 11, "a.bin", "md5v", "sha1v", "md5prefix"));

        assertThat(response.uploadId()).isEqualTo("u1");
        assertThat(response.blockSizeBytes()).isEqualTo(10_485_760L);
        assertThat(response.parts()).singleElement()
                .satisfies(part -> assertThat(part.presignedUrl()).isEqualTo("https://cos/part0"));
        assertThat(response.uploadConfig().retryTimeout()).isEqualTo(300);

        JsonNode body = mapper.readTree(server.calls("POST /v2/groups/GROUP_OPENID/upload_prepare")
                .getFirst().body());
        assertThat(body).isEqualTo(mapper.readTree("""
                {"file_type":4,"file_size":"11","file_name":"a.bin",
                 "md5":"md5v","sha1":"sha1v","md5_10m":"md5prefix"}"""));
    }

    @Test
    void finishesUploadPart() {
        server.stub("POST /v2/users/OPENID_USER/upload_part_finish", 200, "{}");

        api.finishUploadPart(QqMediaApi.Scope.USER, "OPENID_USER",
                UploadPartFinishRequest.of("u1", 1, 10_485_760L, "partmd5"));

        assertThat(mapper.readTree(server.calls("POST /v2/users/OPENID_USER/upload_part_finish").getFirst().body()))
                .isEqualTo(mapper.readTree(
                        "{\"upload_id\":\"u1\",\"part_index\":1,\"block_size\":\"10485760\",\"md5\":\"partmd5\"}"));
    }

    @Test
    void performsFullChunkedUpload(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("blob.bin");
        Files.writeString(file, "0123456789ABCDEFGHIJKLMNO");
        for (int index = 0; index < 3; index++) {
            server.stub("PUT /upload/" + index, 200, "");
            server.stub("POST /v2/users/OPENID_USER/upload_part_finish", 200, "{}");
        }
        server.stub("POST /v2/users/OPENID_USER/upload_prepare", 200, """
                {"upload_id":"u1","block_size":"10","parts":[
                   {"index":0,"presigned_url":"%s/upload/0","block_size":"10"},
                   {"index":1,"presigned_url":"%s/upload/1","block_size":"10"},
                   {"index":2,"presigned_url":"%s/upload/2","block_size":"5"}],
                 "upload_config":{"concurrency":1,"retry_timeout":300,"retry_delay":0}}"""
                .formatted(server.baseUrl(), server.baseUrl(), server.baseUrl()));
        server.stub("POST /v2/users/OPENID_USER/files", 200,
                "{\"file_uuid\":\"uuid_2\",\"file_info\":\"FILE_INFO_2\",\"ttl\":0,\"raw_url\":\"https://cos/get\"}");

        FileUploadResponse response = api.uploadFile(QqMediaApi.Scope.USER, "OPENID_USER",
                FileUploadRequest.TYPE_FILE, file, "blob.bin", true);

        assertThat(response.fileInfo()).isEqualTo("FILE_INFO_2");
        assertThat(response.rawUrl()).isEqualTo("https://cos/get");

        // 分片按 10/10/5 切分并 PUT 到各自的预签名地址
        assertThat(server.calls("PUT /upload/0").getFirst().body()).isEqualTo("0123456789");
        assertThat(server.calls("PUT /upload/1").getFirst().body()).isEqualTo("ABCDEFGHIJ");
        assertThat(server.calls("PUT /upload/2").getFirst().body()).isEqualTo("KLMNO");

        // 每个分片登记：序号、实际字节数、分片 MD5 必须与上传内容一致
        List<FakeQqApiServer.Call> finishes = server.calls("POST /v2/users/OPENID_USER/upload_part_finish");
        assertThat(finishes).hasSize(3);
        assertThat(mapper.readTree(finishes.get(0).body())).isEqualTo(mapper.readTree(
                "{\"upload_id\":\"u1\",\"part_index\":0,\"block_size\":\"10\",\"md5\":\"" + md5("0123456789") + "\"}"));
        assertThat(mapper.readTree(finishes.get(1).body())).isEqualTo(mapper.readTree(
                "{\"upload_id\":\"u1\",\"part_index\":1,\"block_size\":\"10\",\"md5\":\"" + md5("ABCDEFGHIJ") + "\"}"));
        assertThat(mapper.readTree(finishes.get(2).body())).isEqualTo(mapper.readTree(
                "{\"upload_id\":\"u1\",\"part_index\":2,\"block_size\":\"5\",\"md5\":\"" + md5("KLMNO") + "\"}"));

        // 预上传携带整文件与前 10002432 字节的校验值
        JsonNode prepare = mapper.readTree(server.calls("POST /v2/users/OPENID_USER/upload_prepare")
                .getFirst().body());
        assertThat(prepare.path("file_size").stringValue()).isEqualTo("25");
        assertThat(prepare.path("md5").stringValue()).isEqualTo(md5("0123456789ABCDEFGHIJKLMNO"));
        assertThat(prepare.path("md5_10m").stringValue()).isEqualTo(md5("0123456789ABCDEFGHIJKLMNO"));
        assertThat(prepare.path("sha1").stringValue()).isEqualTo(sha1("0123456789ABCDEFGHIJKLMNO"));

        // 合并请求只带 upload_id 与文件名
        assertThat(mapper.readTree(server.calls("POST /v2/users/OPENID_USER/files").getFirst().body()))
                .isEqualTo(mapper.readTree(
                        "{\"file_type\":4,\"file_name\":\"blob.bin\",\"srv_send_msg\":true,\"upload_id\":\"u1\"}"));
    }

    @Test
    void rejectsMissingFile(@TempDir Path tempDir) {
        assertThatThrownBy(() -> api.uploadFile(QqMediaApi.Scope.USER, "OPENID_USER",
                FileUploadRequest.TYPE_FILE, tempDir.resolve("absent.bin"), "absent.bin", false))
                .isInstanceOf(QqApiException.class)
                .hasMessageContaining("absent.bin");
    }

    private static String md5(String text) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha1(String text) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
