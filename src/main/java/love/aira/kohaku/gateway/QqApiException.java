package love.aira.kohaku.gateway;

/** 调用开放平台 HTTP 接口失败。 */
public class QqApiException extends RuntimeException {

    public QqApiException(String message) {
        super(message);
    }

    public QqApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
