package love.aira.kohaku.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaginationTest {

    private static final List<String> ITEMS = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");

    @Test
    void computesPagesAndSlices() {
        Pagination page1 = Pagination.of(1, 5, ITEMS.size());

        assertThat(page1.totalPages()).isEqualTo(3);
        assertThat(page1.label()).isEqualTo("1/3");
        assertThat(page1.hasPrevious()).isFalse();
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.slice(ITEMS)).containsExactly("1", "2", "3", "4", "5");

        Pagination page3 = Pagination.of(3, 5, ITEMS.size());
        assertThat(page3.label()).isEqualTo("3/3");
        assertThat(page3.hasPrevious()).isTrue();
        assertThat(page3.hasNext()).isFalse();
        assertThat(page3.slice(ITEMS)).containsExactly("11", "12");
    }

    @Test
    void clampsOutOfRangePages() {
        assertThat(Pagination.of(99, 5, ITEMS.size()).page()).isEqualTo(3);
        assertThat(Pagination.of(0, 5, ITEMS.size()).page()).isEqualTo(1);
        assertThat(Pagination.of(-5, 5, ITEMS.size()).page()).isEqualTo(1);
    }

    @Test
    void handlesEmptyData() {
        Pagination empty = Pagination.of(2, 5, 0);

        assertThat(empty.totalPages()).isEqualTo(1);
        assertThat(empty.page()).isEqualTo(1);
        assertThat(empty.slice(ITEMS)).isEmpty();
    }

    @Test
    void sliceToleratesShorterListThanTotal() {
        // 列表比 totalItems 短（例如被过滤）：第 1 页取实际长度，后续页按偏移截断
        assertThat(Pagination.of(1, 5, 12).slice(List.of("a", "b", "c"))).containsExactly("a", "b", "c");
        assertThat(Pagination.of(2, 5, 12).slice(List.of("a", "b", "c", "d", "e", "f", "g")))
                .containsExactly("f", "g");
        assertThat(Pagination.of(3, 5, 12).slice(List.of("a", "b", "c"))).isEmpty();
    }

    @Test
    void rejectsNonPositivePageSize() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Pagination.of(1, 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");
    }
}
