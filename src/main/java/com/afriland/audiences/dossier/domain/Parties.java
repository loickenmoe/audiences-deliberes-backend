package com.afriland.audiences.dossier.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parties secondaires d'un dossier (demandeur, défendeur). Le client principal est référencé via
 * {@code dossier.client_id}, pas ici (cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-27). Mappé en JSONB
 * (colonne {@code dossier.parties}) via {@code @JdbcTypeCode(SqlTypes.JSON)} sur l'entité Dossier.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Parties {
    private String demandeur;
    private String defendeur;
}
