package love.aira.kohaku.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PathSegmentsTest {

    @Test
    void keepsUnreservedCharactersAndSubDelimiters() {
        // 真实 OpenID / 消息 ID 里出现的字符必须原样保留
        assertThat(PathSegments.encode("openid", "2D0646F90514807CAE1B0F4D829EE221"))
                .isEqualTo("2D0646F90514807CAE1B0F4D829EE221");
        assertThat(PathSegments.encode("message_id", "ROBOT1.0_L.nB5OHX-htEy5q58KDhA!!"))
                .isEqualTo("ROBOT1.0_L.nB5OHX-htEy5q58KDhA!!");
        assertThat(PathSegments.encode("value", "a~b_c-d.e:f@g!h$i&j'k(l)m*n+o,p;q=r"))
                .isEqualTo("a~b_c-d.e:f@g!h$i&j'k(l)m*n+o,p;q=r");
    }

    @Test
    void encodesPathSeparatorsSpacesAndPercent() {
        assertThat(PathSegments.encode("message_id", "MSG/1")).isEqualTo("MSG%2F1");
        assertThat(PathSegments.encode("message_id", "MSG 2")).isEqualTo("MSG%202");
        assertThat(PathSegments.encode("message_id", "100%")).isEqualTo("100%25");
        assertThat(PathSegments.encode("message_id", "a?b#c")).isEqualTo("a%3Fb%23c");
    }

    @Test
    void encodesNonAsciiAsUtf8Bytes() {
        assertThat(PathSegments.encode("name", "中文")).isEqualTo("%E4%B8%AD%E6%96%87");
        assertThat(PathSegments.encode("name", "é")).isEqualTo("%C3%A9");
        // 代理对（emoji）按码点编码，不产生半个字符
        assertThat(PathSegments.encode("name", "😀")).isEqualTo("%F0%9F%98%80");
    }

    @Test
    void rejectsBlankValues() {
        assertThatThrownBy(() -> PathSegments.encode("channel_id", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel_id must not be blank");
        assertThatThrownBy(() -> PathSegments.encode("channel_id", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel_id must not be blank");
    }
}
