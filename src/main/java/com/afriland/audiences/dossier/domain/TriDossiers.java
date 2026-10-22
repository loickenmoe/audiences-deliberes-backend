package com.afriland.audiences.dossier.domain;

import org.springframework.data.domain.Sort;

/**
 * Sort orders offered on {@code GET /dossiers}.
 * <p><b>A closed vocabulary, not a column name</b>: letting the client name entity fields would
 * cause 500 errors on unknown properties and expose the schema.
 * <p><b>The id always closes the sort</b>, so cases created in the same second (data migration)
 * cannot move between pages.
 * <p>There is no sort by step: grouping is better served by the step type and status filters, and
 * the two cassation steps are concurrent, so there is no single step to sort on.
 */
public enum TriDossiers {

    /** Most recently opened first. The default. */
    RECENT(Sort.by(Sort.Direction.DESC, "createdAt", "id")),

    /** Oldest first: what has been pending the longest. */
    ANCIEN(Sort.by(Sort.Direction.ASC, "createdAt", "id")),

    /** By reference, alphabetically: the paper filing order. */
    REFERENCE(Sort.by(Sort.Direction.ASC, "reference").and(Sort.by(Sort.Direction.ASC, "id"))),

    /** Highest risk first: the order in which the bank reviews them. */
    RISQUE(Sort.by(Sort.Direction.DESC, "risqueEncouru", "id"));

    private final Sort sort;

    TriDossiers(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
