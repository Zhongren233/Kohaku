package love.aira.kohaku.support;

import java.util.List;

/**
 * 分页计算（纯逻辑，便于测试与复用）。
 *
 * <p>{@link #of(int, int, int)} 会把页码夹到有效区间，因此按钮里携带的过期页码不会导致越界。
 *
 * @param page       当前页码，从 1 开始
 * @param pageSize   每页条数
 * @param totalItems 总条数
 */
public record Pagination(int page, int pageSize, int totalItems) {

    public static Pagination of(int requestedPage, int pageSize, int totalItems) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1, was " + pageSize);
        }
        int items = Math.max(0, totalItems);
        int pages = Math.max(1, (int) Math.ceil((double) items / pageSize));
        return new Pagination(Math.min(Math.max(1, requestedPage), pages), pageSize, items);
    }

    public int totalPages() {
        return Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages();
    }

    public int fromIndex() {
        return (page - 1) * pageSize;
    }

    public int toIndex() {
        return Math.min(totalItems, fromIndex() + pageSize);
    }

    /** 取当前页数据；传入列表比 {@link #totalItems} 短时按实际长度截断。 */
    public <T> List<T> slice(List<T> items) {
        int from = Math.min(fromIndex(), items.size());
        int to = Math.min(toIndex(), items.size());
        return List.copyOf(items.subList(from, to));
    }

    /** 页码展示文案，如 {@code 2/5}。 */
    public String label() {
        return page + "/" + totalPages();
    }
}
