package vn.uytinmang.projectos.platform.api;

import java.util.List;

public record PageResponse<T>(List<T> data, Meta meta) {
    public static <T> PageResponse<T> of(List<T> data, int page, int size, long total, int totalPages) {
        return new PageResponse<>(data, new Meta(page, size, total, totalPages));
    }

    public record Meta(int page, int size, long total, int totalPages) {
    }
}
