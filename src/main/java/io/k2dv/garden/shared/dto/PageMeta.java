package io.k2dv.garden.shared.dto;

import lombok.Builder;
import lombok.Getter;

/** Pagination meta for offset-based admin list endpoints. */
@Getter
@Builder
public class PageMeta {
    private final int page;
    private final int pageSize;
    private final long total;
}
