package com.afriland.audiences.delibere.service;

import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.service.ConditionTransitionEtape;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Conditions a deliberation imposes before a step may leave it.
 *
 * <p>A "deliberation delivered" step has two exits: the <b>appeal</b>, which reopens it and opens
 * the next step, and <b>closure</b>.
 */
public final class ConditionsDelibereePourSortie {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ConditionsDelibereePourSortie() {
    }

    /** The authoritative deliberation on this step: the last one rendered; earlier ones were undone. */
    private static Optional<Delibere> dernierRendu(DelibereRepository repository, EtapeProcedure etape) {
        return repository.findFirstByEtapeIdAndResultatIsNotNullOrderByIdDesc(etape.getId());
    }

    /**
     * An appeal may only be lodged against an <b>unfavourable or mixed</b> judgment.
     *
     * <p>{@code DELIBERE_VIDE -> EN_COURS} means "an appeal is lodged" and <b>automatically opens the
     * next step</b>: without this guard, a favourable judgment could start an appeal nobody decided on.
     */
    @Component
    @RequiredArgsConstructor
    public static class RecoursReserveAuxJugementsDefavorables implements ConditionTransitionEtape {

        private final DelibereRepository delibereRepository;

        @Override
        public boolean concerne(StatutCycleVie de, StatutCycleVie vers) {
            return de == StatutCycleVie.DELIBERE_VIDE && vers == StatutCycleVie.EN_COURS;
        }

        @Override
        public Optional<String> empechement(EtapeProcedure etape) {
            Optional<Delibere> delibere = dernierRendu(delibereRepository, etape);
            if (delibere.isEmpty()) {
                return Optional.of("Aucun délibéré rendu sur cette étape : "
                        + "il n'y a pas de décision contre laquelle exercer un recours.");
            }
            if (delibere.get().getResultat() == ResultatDelibere.FAVORABLE) {
                return Optional.of("Le délibéré de cette étape est favorable : "
                        + "un recours ne s'exerce que contre une décision défavorable ou mixte.");
            }
            return Optional.empty();
        }
    }

    /**
     * An appeal must be lodged <b>within the deadline</b>.
     *
     * <p>{@code DelaiRecoursScheduler} closes expired steps only once a day at 6 am; this guard closes
     * the window in between, during which a late appeal could otherwise open the next step.
     */
    @Component
    @RequiredArgsConstructor
    public static class RecoursDansLeDelai implements ConditionTransitionEtape {

        private final DelibereRepository delibereRepository;

        @Override
        public boolean concerne(StatutCycleVie de, StatutCycleVie vers) {
            return de == StatutCycleVie.DELIBERE_VIDE && vers == StatutCycleVie.EN_COURS;
        }

        @Override
        public Optional<String> empechement(EtapeProcedure etape) {
            return dernierRendu(delibereRepository, etape)
                    .map(Delibere::getDateEcheanceRecours)
                    .filter(echeance -> echeance.isBefore(LocalDateTime.now()))
                    .map(echeance -> "Le délai de recours a expiré le " + echeance.format(JOUR)
                            + " : cette étape ne peut plus être rouverte.");
        }
    }

    /**
     * A step cannot be closed until its deliberation has been rendered.
     *
     * <p>Safety net: today a step only reaches {@code DELIBERE_VIDE} through {@code appliquerResultat},
     * which sets the result. It guards against other paths (data migration, manual fix), since closing
     * a step with an unknown outcome would open the way to final decisions, whose only guard is a
     * closed step.
     */
    @Component
    @RequiredArgsConstructor
    public static class ClotureExigeUnResultat implements ConditionTransitionEtape {

        private final DelibereRepository delibereRepository;

        @Override
        public boolean concerne(StatutCycleVie de, StatutCycleVie vers) {
            return de == StatutCycleVie.DELIBERE_VIDE && vers == StatutCycleVie.CLOTURE;
        }

        @Override
        public Optional<String> empechement(EtapeProcedure etape) {
            if (dernierRendu(delibereRepository, etape).isPresent()) {
                return Optional.empty();
            }
            return Optional.of("Aucun délibéré rendu sur cette étape : "
                    + "son issue doit être enregistrée avant la clôture.");
        }
    }
}
