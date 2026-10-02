package com.afriland.audiences.decision.service;

import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.service.ConditionTransitionEtape;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Une étape ne s'archive pas sans que son issue définitive soit enregistrée (métier, 2026-09-29).
 *
 * <p><b>Ce que cela protège.</b> {@code CLOTURE → ARCHIVE} n'exigeait rien. On pouvait donc archiver
 * une étape dont l'adjudication ou la condamnation n'avait jamais été saisie — et l'archivage est un
 * gel : c'est précisément le moment où l'on cesse de revenir sur un dossier pour le compléter. Le
 * manque devenait alors définitif, sans que personne ne s'en aperçoive.
 *
 * <p><b>Une limite à connaître.</b> Toutes les étapes closes ne produisent pas une décision
 * définitive — un désistement, une radiation en laissent l'étape sans adjudication ni condamnation.
 * Pour celles-là, cette règle interdira l'archivage tant que rien n'est enregistré. C'est
 * l'arbitrage du métier du 2026-09-29 ; s'il s'avère trop strict à l'usage, la réponse sera de
 * permettre d'enregistrer une issue « sans décision », pas de lever la garde.
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
