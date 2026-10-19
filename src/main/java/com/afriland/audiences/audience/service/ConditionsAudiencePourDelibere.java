package com.afriland.audiences.audience.service;

import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.service.ConditionTransitionEtape;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * What hearings require before a step goes to deliberation.
 * <p>Two conditions in two classes so that their failures are reported <b>together</b>.
 * <p>They live here rather than in {@code dossier} because only this module knows what a held
 * hearing is: {@code dossier} publishes the contract, {@code audience} implements it, keeping the
 * module dependency direction.
 */
public final class ConditionsAudiencePourDelibere {

    private ConditionsAudiencePourDelibere() {
    }

    private static boolean versLeDelibere(StatutCycleVie de, StatutCycleVie vers) {
        return de == StatutCycleVie.EN_COURS && vers == StatutCycleVie.EN_DELIBERE;
    }

    /**
     * A step only goes to deliberation if a hearing was <b>held</b> on it.
     * <p><b>"Held" means "reported"</b>: {@code TENUE} is only set when the report is entered.
     * <p>The held date remains the <b>planned</b> date, not the entry date; two tests in
     * {@code AudienceServiceTest} lock this behaviour, which this rule depends on.
     */
    @Component
    @RequiredArgsConstructor
    public static class AudienceTenueRequise implements ConditionTransitionEtape {

        private final AudienceRepository audienceRepository;

        @Override
        public boolean concerne(StatutCycleVie de, StatutCycleVie vers) {
            return versLeDelibere(de, vers);
        }

        @Override
        public Optional<String> empechement(EtapeProcedure etape) {
            if (audienceRepository.existsByEtapeIdAndStatut(etape.getId(), StatutAudience.TENUE)) {
                return Optional.empty();
            }
            return Optional.of("Aucune audience tenue sur cette étape : "
                    + "le compte rendu d'au moins une audience doit être saisi avant la mise en délibéré.");
        }
    }

    /**
     * No hearing may remain <b>scheduled</b> on a step put into deliberation: all its hearings must
     * be held or cancelled. Otherwise a scheduled hearing would keep feeding the calendar and
     * J-7/J-3/J-1 reminders.
     */
    @Component
    @RequiredArgsConstructor
    public static class AucuneAudienceEnAttente implements ConditionTransitionEtape {

        private final AudienceRepository audienceRepository;

        @Override
        public boolean concerne(StatutCycleVie de, StatutCycleVie vers) {
            return versLeDelibere(de, vers);
        }

        @Override
        public Optional<String> empechement(EtapeProcedure etape) {
            long enAttente = audienceRepository.countByEtapeIdAndStatut(etape.getId(), StatutAudience.PLANIFIEE);
            if (enAttente == 0) {
                return Optional.empty();
            }
            // State the count so the user knows how many hearings are concerned.
            return Optional.of(enAttente == 1
                    ? "Une audience reste planifiée sur cette étape : "
                            + "tenez-la ou annulez-la avant la mise en délibéré."
                    : enAttente + " audiences restent planifiées sur cette étape : "
                            + "tenez-les ou annulez-les avant la mise en délibéré.");
        }
    }
}
