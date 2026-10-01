package love.aira.kohaku.api;

/**
 * 开放平台 HTTP 接口调用失败。
 *
 * <p>两种来源：HTTP 层失败（{@link #httpStatus()} 有效，如 401/404）与业务层失败
 * （HTTP 2xx 但响应体 code 非 0，{@link #code()} 有效）。
 */
public class QqApiException extends RuntimeException {

    private final int httpStatus;
    private final int code;
    private final transient String responseBody;

    public QqApiException(String message) {
        this(0, 0, message, null);
    }

    public QqApiException(String message, Throwable cause) {
        this(0, 0, message, null, cause);
    }

    public QqApiException(int httpStatus, int code, String message, String responseBody) {
        this(httpStatus, code, message, responseBody, null);
    }

    public QqApiException(int httpStatus, int code, String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.code = code;
        this.responseBody = responseBody;
    }

    /** HTTP 状态码，非 HTTP 层失败时为 0。 */
    public int httpStatus() {
        return httpStatus;
    }

    /** 平台业务错误码，无错误码时为 0。 */
    public int code() {
        return code;
    }

    /** 原始响应体，便于排查。 */
    public String responseBody() {
        return responseBody;
    }
}
