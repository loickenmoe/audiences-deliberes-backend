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
 * Ce que le délibéré exige avant qu'une étape le quitte (métier, 2026-09-29).
 *
 * <p>Une étape « Délibéré vidé » a deux sorties : le <b>recours</b>, qui la rouvre et ouvre l'étape
 * suivante, et la <b>clôture</b>. Ni l'une ni l'autre ne consultait le délibéré — on pouvait donc
 * faire appel d'un jugement gagné.
 */
public final class ConditionsDelibereePourSortie {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ConditionsDelibereePourSortie() {
    }

    /**
     * Le délibéré qui fait foi sur cette étape : le dernier rendu.
     *
     * <p>Une étape peut en porter plusieurs — prorogée, rabattue, remise en délibéré. C'est le
     * dernier <b>rendu</b> qui décide de ce qui peut suivre ; les précédents ont été défaits.
     */
    private static Optional<Delibere> dernierRendu(DelibereRepository repository, EtapeProcedure etape) {
        return repository.findFirstByEtapeIdAndResultatIsNotNullOrderByIdDesc(etape.getId());
    }

    /**
     * Un recours ne s'exerce que contre un jugement <b>défavorable ou mixte</b>.
     *
     * <p>La règle était déjà écrite — {@code ResultatDelibere} porte ce commentaire depuis M7 :
     * « Seuls DEFAVORABLE et MIXTE ouvrent une voie de recours (Q-42) » — mais rien ne l'appliquait.
     * {@code DELIBERE_VIDE → EN_COURS} signifie « un recours est exercé » et <b>ouvre
     * automatiquement l'étape suivante</b> : sans cette garde, un jugement favorable pouvait faire
     * naître une instance d'appel que personne n'avait décidée.
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
     * Un recours s'exerce <b>dans le délai</b>.
     *
     * <p>{@code DelaiRecoursScheduler} clôture les étapes dont le délai a expiré, mais il passe une
     * fois par jour à 6 h. Entre l'expiration réelle et son passage, la transition restait ouverte :
     * un recours pouvait être enregistré hors délai et faire naître l'étape suivante.
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
     * Une étape ne se clôture pas sans que son délibéré soit rendu.
     *
     * <p><b>Filet, et non correction d'un défaut constaté.</b> Aujourd'hui une étape n'atteint
     * {@code DELIBERE_VIDE} que par {@code appliquerResultat}, qui pose précisément le résultat :
     * cette condition ne peut donc pas se déclencher. Elle est écrite pour le jour où une autre voie
     * mènerait à ce statut — une reprise de données, un correctif — et où clôturer une étape dont
     * on ignore l'issue ouvrirait la porte aux décisions définitives, dont la seule garde est
     * « étape CLOTURE ».
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
