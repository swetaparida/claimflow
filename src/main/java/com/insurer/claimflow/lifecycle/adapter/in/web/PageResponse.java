package com.insurer.claimflow.lifecycle.adapter.in.web;

import com.insurer.claimflow.shared.domain.PageResult;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(PageResult<T> page) {
        return new PageResponse<>(page.content(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
