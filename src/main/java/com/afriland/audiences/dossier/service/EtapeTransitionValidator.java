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
 * La transition {@code EN_DELIBERE → DELIBERE_VIDE} (enregistrement d'un délibéré,
 * RG-DEL-01/FR-DEL-01) ne figure volontairement <b>pas</b> ici : elle est réservée à l'endpoint
 * dédié {@code POST /dossiers/{id}/deliberes} du module délibéré (M7), qui collecte des données
 * métier (résultat, échéance du délai de recours) que cet endpoint générique ne reçoit pas —
 * l'autoriser ici permettrait de la contourner.
 * <p>
 * {@code DELIBERE_VIDE → EN_COURS} reste en revanche autorisée ici : c'est la transition
 * de <b>l'exercice d'un recours</b> ({@link #estExerciceDeRecours}, RG-DOS-04), qui ouvre
 * automatiquement l'étape suivante. Le <b>rabattement</b> (RG-DEL-07/FR-DEL-05, M7) porte sur la
 * même valeur de statut mais via son propre endpoint dédié {@code POST
 * /deliberes/{id}/rabattement}, en dehors de ce validateur générique : il ne rouvre jamais
 * l'étape suivante et exige un motif que cet endpoint générique ne reçoit pas non plus
 * (cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-42 pour la distinction des deux cas).
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
