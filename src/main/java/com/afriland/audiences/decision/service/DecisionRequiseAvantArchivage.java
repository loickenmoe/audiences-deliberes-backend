package com.afriland.audiences.decision.service;

import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.service.ConditionTransitionEtape;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * A step cannot be archived until its final decision is recorded. Archiving freezes the case file, so
 * a missing adjudication or judgment would otherwise become permanent unnoticed.
 * <p>
 * Limitation: some closed steps (withdrawal, striking out) produce no final decision and therefore
 * cannot be archived either.
 */
@Component
@RequiredArgsConstructor
public class DecisionRequiseAvantArchivage implements ConditionTransitionEtape {

    private final DecisionDefinitiveRepository decisionDefinitiveRepository;

    @Override
    public boolean concerne(StatutCycleVie de, StatutCycleVie vers) {
        return de == StatutCycleVie.CLOTURE && vers == StatutCycleVie.ARCHIVE;
    }

    @Override
    public Optional<String> empechement(EtapeProcedure etape) {
        if (decisionDefinitiveRepository.existsByEtapeId(etape.getId())) {
            return Optional.empty();
        }
        return Optional.of("Aucune décision définitive n'est enregistrée sur cette étape : "
                + "l'adjudication ou la condamnation doit être saisie avant l'archivage.");
    }
}
