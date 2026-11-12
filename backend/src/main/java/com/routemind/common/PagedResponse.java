package com.routemind.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable shape for list endpoints. Spring Data's Page serializes
 * unpredictably as JSON (the framework itself warns against it), so
 * controllers convert to this record instead. Callers get the items plus
 * the pagination metadata they need to page through them.
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, D> PagedResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
