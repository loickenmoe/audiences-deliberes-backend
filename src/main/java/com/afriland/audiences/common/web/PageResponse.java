package com.afriland.audiences.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Pagination envelope matching the API contract's paged responses:
 * {@code {content, totalPages, totalElements}}.
 */
public record PageResponse<T>(List<T> content, int totalPages, long totalElements) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalPages(), page.getTotalElements());
    }

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getTotalPages(), page.getTotalElements());
    }
}
