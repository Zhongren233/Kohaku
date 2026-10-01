package love.aira.kohaku.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 发送消息响应的扩展信息。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageExtInfo(String refIdx) {
}
