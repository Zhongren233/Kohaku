package love.aira.kohaku.api;

import java.net.http.HttpResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

/**
 * 开放平台响应校验：非 2xx（如 401/404）与 2xx 但业务 {@code code != 0} 都转成 {@link QqApiException}。
 */
final class ApiResponses {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ApiResponses() {
    }

    static JsonNode requireSuccess(HttpResponse<String> response) {
        String body = response.body();
        JsonNode payload = body == null || body.isBlank() ? NullNode.getInstance() : parse(body);
        if (response.statusCode() / 100 != 2) {
            throw new QqApiException(response.statusCode(), payload.path("code").asInt(0),
                    "HTTP " + response.statusCode() + " from " + response.uri().getPath() + ": "
                            + describe(payload, body), body);
        }
        int code = payload.path("code").asInt(0);
        if (code != 0) {
            throw new QqApiException(response.statusCode(), code,
                    "code " + code + " from " + response.uri().getPath() + ": " + describe(payload, body), body);
        }
        return payload;
    }

    private static String describe(JsonNode payload, String body) {
        String message = payload.path("message").stringValue(null);
        return message == null || message.isBlank() ? String.valueOf(body) : message;
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (JacksonException e) {
            return NullNode.getInstance();
        }
    }
}
