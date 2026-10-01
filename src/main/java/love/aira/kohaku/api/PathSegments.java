package love.aira.kohaku.api;

import java.nio.charset.StandardCharsets;

/**
 * 路径段构造：校验非空并按 RFC 3986 对 path segment 做 UTF-8 百分号编码。
 *
 * <p>保留未保留字符与子分隔符（{@code A-Za-z0-9-._~!$&'()*+,;=:@}），其余（含 {@code /}、空格、非 ASCII）
 * 逐字节编码为大写十六进制，行为对齐原先使用的 spring-web {@code UriUtils.encodePathSegment}，
 * 以避免消息 ID 等不透明参数中的特殊字符破坏路径。
 */
final class PathSegments {

    private static final String ALLOWED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,;=:@";

    private PathSegments() {
    }

    static String encode(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        StringBuilder encoded = new StringBuilder(value.length() + 8);
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint < 128 && ALLOWED.indexOf((char) codePoint) >= 0) {
                encoded.append((char) codePoint);
            } else {
                for (byte value1 : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
                    encoded.append('%')
                            .append(Character.toUpperCase(Character.forDigit((value1 >> 4) & 0xF, 16)))
                            .append(Character.toUpperCase(Character.forDigit(value1 & 0xF, 16)));
                }
            }
        }
        return encoded.toString();
    }
}
