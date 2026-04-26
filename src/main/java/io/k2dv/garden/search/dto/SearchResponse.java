package io.k2dv.garden.search.dto;

import io.k2dv.garden.collection.dto.response.CollectionSummaryResponse;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.shared.dto.PagedResult;

public record SearchResponse(
    PagedResult<ProductSummaryResponse> products,
    PagedResult<CollectionSummaryResponse> collections,
    PagedResult<SearchArticleResult> articles,
    PagedResult<SearchPageResult> pages
) {}
