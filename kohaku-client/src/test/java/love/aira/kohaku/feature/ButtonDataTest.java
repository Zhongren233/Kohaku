package love.aira.kohaku.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ButtonDataTest {

    @Test
    void roundTripsFeatureActionAndState() {
        Map<String, String> state = new java.util.LinkedHashMap<>();
        state.put("p", "3");
        state.put("q", "测试 值");
        String data = ButtonData.encode("card", "page", state);

        assertThat(data).isEqualTo("card:page:p=3;q=%E6%B5%8B%E8%AF%95%20%E5%80%BC");
        ButtonData.Parts parts = ButtonData.decode(data);
        assertThat(parts.featureId()).isEqualTo("card");
        assertThat(parts.action()).isEqualTo("page");
        assertThat(parts.state()).containsEntry("p", "3").containsEntry("q", "测试 值");
        assertThat(parts.intState("p", 1)).isEqualTo(3);
        assertThat(parts.intState("missing", 7)).isEqualTo(7);
        assertThat(parts.intState("q", 9)).isEqualTo(9);   // 非数字 → 默认值
    }

    @Test
    void encodesWithoutState() {
        assertThat(ButtonData.encode("card", "next", Map.of())).isEqualTo("card:next");
        assertThat(ButtonData.encode("card", "next", null)).isEqualTo("card:next");
        assertThat(ButtonData.decode("card:next").state()).isEmpty();
    }

    @Test
    void valuesMayContainSeparatorsAndUnicode() {
        String data = ButtonData.encode("card", "search", Map.of("q", "a:b;c=d", "tag", "中文"));

        ButtonData.Parts parts = ButtonData.decode(data);
        assertThat(parts.state()).containsEntry("q", "a:b;c=d").containsEntry("tag", "中文");
    }

    @Test
    void rejectsInvalidData() {
        assertThat(ButtonData.decode(null)).isNull();
        assertThat(ButtonData.decode("  ")).isNull();
        assertThat(ButtonData.decode("card")).isNull();               // 缺 action
        assertThat(ButtonData.decode(":next")).isNull();              // 缺 featureId
        assertThat(ButtonData.decode("card :next")).isNull();         // featureId 含非法字符
    }

    @Test
    void rejectsUnsafeNamesWhenEncoding() {
        assertThatThrownBy(() -> ButtonData.encode("bad:id", "next", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("featureId");
        assertThatThrownBy(() -> ButtonData.encode("card", "bad action", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("action");
    }

    @Test
    void skipsMalformedStatePairs() {
        ButtonData.Parts parts = ButtonData.decode("card:next:p=2;broken;=x");

        assertThat(parts.state()).containsExactly(Map.entry("p", "2"));
    }
}
