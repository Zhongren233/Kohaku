package love.aira.kohaku.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import love.aira.kohaku.config.KohakuConfig;
import love.aira.kohaku.internal.SnakeCaseMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 开放平台 REST 调用器：统一拼接 base url、注入 {@code Authorization}、解析 JSON、
 * 把 HTTP 层与业务层错误转成 {@link QqApiException}，并在 401/403 时刷新 AccessToken 重试一次。
 *
 * <p>平台两种错误形态都会处理：非 2xx（如 401/404）与 2xx 但响应体 {@code code != 0}。
 */
public class QqOpenApiClient {

    private static final Logger log = LoggerFactory.getLogger(QqOpenApiClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final KohakuConfig config;
    private final AccessTokenProvider tokens;

    public QqOpenApiClient(HttpClient qqHttpClient, ObjectMapper mapper, KohakuConfig config,
                           AccessTokenProvider tokens) {
        this.httpClient = qqHttpClient;
        // 平台字段是 snake_case：派生专用实例，避免改动宿主/全局 JSON 配置
        this.mapper = SnakeCaseMappers.of(mapper);
        this.config = config;
        this.tokens = tokens;
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return convert(post(path, body), type);
    }

    public JsonNode post(String path, Object body) {
        String json = body == null ? "{}" : mapper.writeValueAsString(body);
        if (log.isDebugEnabled()) {
            log.debug("QQ api -> POST {} {}", path, json);
        }
        return execute(authorization -> request(path, authorization)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    public <T> T get(String path, Class<T> type) {
        return convert(get(path), type);
    }

    public JsonNode get(String path) {
        return execute(authorization -> request(path, authorization).GET().build());
    }

    public <T> T delete(String path, Class<T> type) {
        return convert(delete(path), type);
    }

    public JsonNode delete(String path) {
        return execute(authorization -> request(path, authorization).DELETE().build());
    }

    public <T> T put(String path, Object body, Class<T> type) {
        return convert(put(path, body), type);
    }

    /** PUT 提交 JSON（如应答互动事件）。 */
    public JsonNode put(String path, Object body) {
        String json = body == null ? "{}" : mapper.writeValueAsString(body);
        if (log.isDebugEnabled()) {
            log.debug("QQ api -> PUT {} {}", path, json);
        }
        return execute(authorization -> request(path, authorization)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    /**
     * multipart/form-data 提交：频道与私信发送接口支持直接用 form-data 上传图片文件（{@code file_image}）。
     *
     * <p>对象/数组字段按文档要求序列化为 JSON 字符串；字段集合用 {@link #formFields(Object)} 从请求对象生成。
     */
    public <T> T postMultipart(String path, Map<String, Object> fields, String fileField, Path file, Class<T> type) {
        MultipartBody multipart = multipartBody(fields, fileField, file);
        JsonNode payload = execute(authorization -> request(path, authorization)
                .header("Content-Type", multipart.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.body()))
                .build());
        return convert(payload, type);
    }

    /** 把请求对象按 snake_case 转成表单字段（null 字段被忽略，对象/数组保留为 JSON 值）。 */
    public Map<String, Object> formFields(Object body) {
        JsonNode node = mapper.valueToTree(body);
        Map<String, Object> fields = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
        }
        return fields;
    }

    private record MultipartBody(byte[] body, String contentType) {
    }

    private MultipartBody multipartBody(Map<String, Object> fields, String fileField, Path file) {
        String boundary = "----kohaku-" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fields.forEach((name, value) -> writeTextField(out, boundary, name, value));
        writeFilePart(out, boundary, fileField, file);
        write(out, "--" + boundary + "--\r\n");
        return new MultipartBody(out.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }

    private void writeTextField(ByteArrayOutputStream out, String boundary, String name, Object value) {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(out, value instanceof String text ? text
                : value instanceof JsonNode node && node.isString() ? node.stringValue()
                : mapper.writeValueAsString(value));
        write(out, "\r\n");
    }

    private void writeFilePart(ByteArrayOutputStream out, String boundary, String fileField, Path file) {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fileField + "\"; filename=\""
                + file.getFileName() + "\"\r\nContent-Type: " + mediaType(file) + "\r\n\r\n");
        try {
            out.write(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new QqApiException("cannot read file " + file + ": " + e.getMessage(), e);
        }
        write(out, "\r\n");
    }

    private static String mediaType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder request(String path, String authorization) {
        return HttpRequest.newBuilder(URI.create(config.effectiveApiBaseUrl() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorization);
    }

    private JsonNode execute(Function<String, HttpRequest> requestFactory) {
        HttpResponse<String> response = HttpCalls.send(httpClient, requestFactory.apply(tokens.authorization()));
        if (isUnauthorized(response.statusCode())) {
            tokens.invalidate();
            response = HttpCalls.send(httpClient, requestFactory.apply(tokens.authorization()));
        }
        if (log.isDebugEnabled()) {
            log.debug("QQ api <- {} {} {}", response.request().method(), response.uri().getPath(), response.body());
        }
        return ApiResponses.requireSuccess(response);
    }

    private <T> T convert(JsonNode payload, Class<T> type) {
        try {
            return mapper.treeToValue(payload, type);
        } catch (JacksonException e) {
            throw new QqApiException("cannot map response to " + type.getSimpleName() + ": " + payload, e);
        }
    }

    private static boolean isUnauthorized(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }
}
