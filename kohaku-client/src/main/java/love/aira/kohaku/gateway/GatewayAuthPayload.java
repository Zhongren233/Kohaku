package love.aira.kohaku.gateway;

import java.util.regex.Pattern;
import love.aira.kohaku.config.KohakuConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 鉴权报文构造（OpCode 2 Identify / 6 Resume）与日志脱敏。
 *
 * <p>提出来是为了可测：报文结构只由配置、凭据与 session 状态决定，与连接生命周期无关。
 */
final class GatewayAuthPayload {

    /** Identify/Resume 报文携带 AccessToken，日志必须先脱敏。 */
    private static final Pattern AUTH_TOKEN = Pattern.compile("(\"token\"\\s*:\\s*\")[^\"]*(\")");

    private GatewayAuthPayload() {
    }

    /** OpCode 2 Identify：intents、分片与客户端属性。 */
    static ObjectNode identify(ObjectMapper mapper, KohakuConfig config, String token) {
        ObjectNode data = mapper.createObjectNode();
        data.put("token", token);
        data.put("intents", config.intentsMask());
        ArrayNode shard = data.putArray("shard");
        shard.add(config.shardIndex());
        shard.add(config.shardTotal());
        ObjectNode client = data.putObject("properties");
        client.put("$os", System.getProperty("os.name", "unknown"));
        client.put("$browser", config.clientName());
        client.put("$device", config.clientName());
        return envelope(mapper, GatewayOp.IDENTIFY, data);
    }

    /** OpCode 6 Resume：session_id 与已收到的 seq，让网关补发遗漏事件。 */
    static ObjectNode resume(ObjectMapper mapper, String token, String sessionId, long seq) {
        ObjectNode data = mapper.createObjectNode();
        data.put("token", token);
        data.put("session_id", sessionId);
        data.put("seq", seq);
        return envelope(mapper, GatewayOp.RESUME, data);
    }

    private static ObjectNode envelope(ObjectMapper mapper, int op, ObjectNode data) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("op", op);
        payload.set("d", data);
        return payload;
    }

    /** 日志脱敏：把 {@code "token":"…"} 的值替换成 {@code ***}。 */
    static String redact(String json) {
        return AUTH_TOKEN.matcher(json).replaceAll("$1***$2");
    }
}
