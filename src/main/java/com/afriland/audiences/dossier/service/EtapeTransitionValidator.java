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
 * Lifecycle transitions allowed through the generic {@code PATCH
 * /dossiers/{id}/etapes/{etapeId}/statut} (RG-DOS-04).
 * <p>
 * {@code EN_DELIBERE → DELIBERE_VIDE} (recording a deliberation, RG-DEL-01/FR-DEL-01) is
 * deliberately <b>not</b> here: it is reserved for {@code POST /dossiers/{id}/deliberes}, which
 * collects data this endpoint does not receive.
 * <p>
 * {@code DELIBERE_VIDE → EN_COURS} is allowed here as <b>lodging an appeal</b>
 * ({@link #estExerciceDeRecours}), which opens the next step. The <b>reversal</b>
 * (RG-DEL-07/FR-DEL-05) uses the same status but its own endpoint {@code POST
 * /deliberes/{id}/rabattement}: it never opens the next step and requires a reason.
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

    /**
     * {@code DELIBERE_VIDE → EN_COURS} through this endpoint means an appeal was lodged
     * (RG-DOS-04).
     */
    public boolean estExerciceDeRecours(StatutCycleVie de, StatutCycleVie vers) {
        return de == DELIBERE_VIDE && vers == EN_COURS;
    }
}
