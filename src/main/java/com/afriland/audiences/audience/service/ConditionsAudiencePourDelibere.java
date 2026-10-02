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
 * Ce que les audiences exigent avant qu'une étape parte en délibéré (métier, 2026-09-29).
 *
 * <p>Deux conditions, portées par deux classes pour que leurs manques se disent <b>ensemble</b> :
 * une étape sans audience tenue et avec une audience encore planifiée doit apprendre les deux d'un
 * coup, pas l'une après l'autre.
 *
 * <p><b>Pourquoi ici et pas dans {@code dossier}.</b> Ces deux règles parlent d'audiences ; seul ce
 * module sait ce qu'est une audience tenue. {@code dossier} publie le contrat, {@code audience}
 * l'implémente — le sens de la dépendance reste celui du graphe des modules.
 */
public final class ConditionsAudiencePourDelibere {

    private ConditionsAudiencePourDelibere() {
    }

    private static boolean versLeDelibere(StatutCycleVie de, StatutCycleVie vers) {
        return de == StatutCycleVie.EN_COURS && vers == StatutCycleVie.EN_DELIBERE;
    }

    /**
     * Une étape ne part en délibéré que si une audience s'y est <b>tenue</b>.
     *
     * <p><b>« Tenue » veut dire « rapportée »</b>, sur arbitrage du métier : le statut {@code TENUE}
     * n'est posé qu'à la saisie du compte rendu. Mettre en délibéré sans compte rendu laisserait le
     * dossier décrire une procédure dont rien ne dit ce qui s'y est passé.
     *
     * <p>La date de tenue reste la date <b>planifiée</b> et non celle de la saisie (QF-32,
     * 2026-09-23) : un compte rendu rédigé trois jours après l'audience n'en déplace pas la date.
     * Deux tests de {@code AudienceServiceTest} verrouillent ce point, dont cette règle dépend.
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
     * Aucune audience ne doit rester <b>planifiée</b> sur l'étape mise en délibéré.
     *
     * <p>Exigence du métier, et la plus stricte des deux : une étape ne part en délibéré que lorsque
     * toutes ses audiences sont réglées — tenues ou annulées. Sans elle, une audience planifiée
     * survivait à la mise en délibéré et continuait d'alimenter le calendrier et les rappels
     * J-7/J-3/J-1 d'une étape qui n'attendait plus rien.
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
            // Le nombre est dit : « des audiences » enverrait chercher combien, et lesquelles.
            return Optional.of(enAttente == 1
                    ? "Une audience reste planifiée sur cette étape : "
                            + "tenez-la ou annulez-la avant la mise en délibéré."
                    : enAttente + " audiences restent planifiées sur cette étape : "
                            + "tenez-les ou annulez-les avant la mise en délibéré.");
        }
    }
}
