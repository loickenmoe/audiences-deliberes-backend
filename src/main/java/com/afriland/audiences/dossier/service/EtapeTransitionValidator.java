package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.afriland.audiences.dossier.domain.StatutCycleVie.ARCHIVE;
import static com.afriland.audiences.dossier.domain.StatutCycleVie.CLOTURE;
import static com.afriland.audiences.dossier.domain.StatutCycleVie.DELIBERE_VIDE;
import static com.afriland.audiences.dossier.domain.StatutCycleVie.EN_COURS;
import static com.afriland.audiences.dossier.domain.StatutCycleVie.EN_DELIBERE;
import static com.afriland.audiences.dossier.domain.StatutCycleVie.OUVERTURE;

/**
 * Transitions de cycle de vie autorisées via l'endpoint générique
 * {@code PATCH /dossiers/{id}/etapes/{etapeId}/statut} (RG-DOS-04).
 * <p>
 * Les transitions {@code EN_DELIBERE → DELIBERE_VIDE} (enregistrement d'un délibéré,
 * RG-DEL-01/FR-DEL-01) et {@code EN_DELIBERE → EN_COURS} (rabattement, RG-DEL-07/FR-DEL-05) ne
 * figurent volontairement <b>pas</b> ici : elles sont réservées aux endpoints dédiés du module
 * délibéré (M7), qui collectent des données métier (résultat, motif) que cet endpoint générique
 * ne reçoit pas — les autoriser ici permettrait de les contourner.
 */
@Component
public class EtapeTransitionValidator {

    private static final Map<StatutCycleVie, Set<StatutCycleVie>> TRANSITIONS_AUTORISEES = Map.of(
            OUVERTURE, Set.of(EN_COURS),
            EN_COURS, Set.of(EN_DELIBERE),
            EN_DELIBERE, Set.of(),
            DELIBERE_VIDE, Set.of(CLOTURE, EN_COURS),
            CLOTURE, Set.of(ARCHIVE),
            ARCHIVE, Set.of()
    );

    public boolean estAutorisee(StatutCycleVie de, StatutCycleVie vers) {
        return TRANSITIONS_AUTORISEES.getOrDefault(de, Set.of()).contains(vers);
    }

    /** {@code DELIBERE_VIDE → EN_COURS} via cet endpoint ne signifie qu'une chose : un recours a été exercé (RG-DOS-04). */
    public boolean estExerciceDeRecours(StatutCycleVie de, StatutCycleVie vers) {
        return de == DELIBERE_VIDE && vers == EN_COURS;
    }
}
