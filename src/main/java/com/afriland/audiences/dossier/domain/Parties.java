package com.afriland.audiences.dossier.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Parties to the proceedings: claimants and defendants, several of each.
 * <p>The main client is referenced by {@code dossier.client_id} and <b>is not repeated here</b>:
 * its identity in the reference data is authoritative, not a copied string.
 * <p>Stored as JSONB in {@code dossier.parties} via {@code @JdbcTypeCode(SqlTypes.JSON)} on
 * {@code Dossier}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Parties {

    /** At least one. Never {@code null}: an empty list otherwise. */
    private List<String> demandeurs = new ArrayList<>();

    /** At least one. Never {@code null}: an empty list otherwise. */
    private List<String> defendeurs = new ArrayList<>();
}
