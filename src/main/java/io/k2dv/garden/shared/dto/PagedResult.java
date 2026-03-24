package io.k2dv.garden.shared.dto;

import lombok.Getter;

import java.util.List;
import java.util.function.Function;

@Getter
public class PagedResult<T> {

    private final List<T> content;
    private final PageMeta meta;

    public PagedResult(List<T> content, PageMeta meta) {
        this.content = content;
        this.meta = meta;
    }

    public static <T> PagedResult<T> of(org.springframework.data.domain.Page<T> page) {
        return new PagedResult<>(
            page.getContent(),
            PageMeta.builder()
                .page(page.getNumber())
                .pageSize(page.getSize())
                .total(page.getTotalElements())
                .build()
        );
    }

    public static <T, R> PagedResult<R> of(org.springframework.data.domain.Page<T> page, Function<T, R> mapper) {
        return new PagedResult<>(
            page.getContent().stream().map(mapper).toList(),
            PageMeta.builder()
                .page(page.getNumber())
                .pageSize(page.getSize())
                .total(page.getTotalElements())
                .build()
        );
    }
}
