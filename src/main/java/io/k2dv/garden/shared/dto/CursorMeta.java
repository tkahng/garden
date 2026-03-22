package io.k2dv.garden.shared.dto;

import lombok.Builder;
import lombok.Getter;

/** Pagination meta for cursor-based list endpoints. */
@Getter
@Builder
public class CursorMeta {
    private final String nextCursor;   // null when no more results
    private final boolean hasMore;
    private final int pageSize;
}
