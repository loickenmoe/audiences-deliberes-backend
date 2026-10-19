package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * A level's hearings cannot be opened while the previous level awaits judgment.
 * <p>"The previous step must be closed" would be unworkable: lodging an appeal moves the current
 * step back to {@code EN_COURS} before creating the next one in {@code OUVERTURE} (RG-DOS-04).
 * <p>What holds is that <b>one does not appeal before the judge has ruled</b>: an earlier step
 * {@code EN_DELIBERE} with a higher level {@code EN_COURS} is an impossible state, reachable
 * through direct positioning.
 * <p><b>The two cassation appeals are concurrent</b>: the CCJA appeal's predecessors are Instance
 * and Appeal, <b>never</b> the Supreme Court. The table mirrors {@code etapesPrecedentes} rather
 * than being derived from rank.
 */
@Component
@RequiredArgsConstructor
public class AucuneEtapeAnterieureEnDelibere implements ConditionTransitionEtape {

    private final EtapeProcedureRepository etapeProcedureRepository;

    @Override
    public boolean concerne(StatutCycleVie de, StatutCycleVie vers) {
        // Only opening the hearings is concerned. Resuming after an appeal starts from
        // DELIBERE_VIDE on the step itself.
        return de == StatutCycleVie.OUVERTURE && vers == StatutCycleVie.EN_COURS;
    }

    @Override
    public Optional<String> empechement(EtapeProcedure etape) {
        if (etape.getDossier() == null || etape.getType() == null) {
            return Optional.empty();
        }
        List<TypeEtape> anterieures = antecedents(etape.getType());
        if (anterieures.isEmpty()) {
            return Optional.empty();
        }

        return etapeProcedureRepository.findByDossierIdOrderByTypeAsc(etape.getDossier().getId()).stream()
                .filter(autre -> anterieures.contains(autre.getType()))
                .filter(autre -> autre.getStatutCycleVie() == StatutCycleVie.EN_DELIBERE)
                .findFirst()
                .map(enDelibere -> "L'étape « " + libelle(enDelibere.getType()) + " » est en délibéré : "
                        + "son jugement doit être rendu avant d'ouvrir les débats à ce degré.");
    }

    /** Mirror of {@code DossierService.etapesPrecedentes}: the levels preceding this one. */
    private static List<TypeEtape> antecedents(TypeEtape type) {
        return switch (type) {
            case INSTANCE -> List.of();
            case RECOURS_1 -> List.of(TypeEtape.INSTANCE);
            case RECOURS_2, RECOURS_2_CCJA -> List.of(TypeEtape.INSTANCE, TypeEtape.RECOURS_1);
        };
    }

    /** The name the user reads on screen, not the enum value. */
    private static String libelle(TypeEtape type) {
        return switch (type) {
            case INSTANCE -> "Instance";
            case RECOURS_1 -> "Appel";
            case RECOURS_2 -> "Pourvoi en cassation";
            case RECOURS_2_CCJA -> "Pourvoi CCJA";
        };
    }
}
