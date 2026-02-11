package ru.practicum.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableUtil {
    private PageableUtil() {
    }

    public static Pageable fromOffset(int from, int size, Sort sort) {
        int page = from / size;
        return PageRequest.of(page, size, sort);
    }

    public static Pageable fromOffset(int from, int size) {
        int page = from / size;
        return PageRequest.of(page, size);
    }
}
