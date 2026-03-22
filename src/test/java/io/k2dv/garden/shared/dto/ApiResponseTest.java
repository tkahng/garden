package io.k2dv.garden.shared.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void of_wrapsData() {
        var response = ApiResponse.of("hello");
        assertThat(response.getData()).isEqualTo("hello");
        assertThat(response.getMeta()).isNull();
    }

    @Test
    void withCursor_includesMeta() {
        var meta = CursorMeta.builder()
                .nextCursor("abc123")
                .hasMore(true)
                .pageSize(20)
                .build();
        var response = ApiResponse.<String>builder()
                .data("hello")
                .meta(meta)
                .build();

        assertThat(response.getMeta()).isInstanceOf(CursorMeta.class);
        var cursor = (CursorMeta) response.getMeta();
        assertThat(cursor.getNextCursor()).isEqualTo("abc123");
        assertThat(cursor.isHasMore()).isTrue();
    }
}
