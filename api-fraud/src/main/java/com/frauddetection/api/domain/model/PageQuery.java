package com.frauddetection.api.domain.model;

/** Pagination et tri, indépendants du framework. */
public record PageQuery(int page, int size, String sortBy, SortDirection direction) {

    public static final int MAX_SIZE = 200;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page doit être >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size doit être compris entre 1 et " + MAX_SIZE);
        }
        if (sortBy == null || sortBy.isBlank()) {
            throw new IllegalArgumentException("sortBy est obligatoire");
        }
        if (direction == null) {
            direction = SortDirection.DESC;
        }
    }

    public enum SortDirection { ASC, DESC }
}
