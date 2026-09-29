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
 * On n'ouvre pas les débats d'un degré tant que le précédent attend son jugement (2026-09-29).
 *
 * <p><b>Pourquoi cette forme, et pas « l'étape précédente doit être close ».</b> C'est la règle que
 * le métier avait validée, et elle s'est révélée <b>impossible</b> : l'exercice d'un recours fait
 * passer l'étape courante à {@code EN_COURS} — elle se rouvre — avant de créer la suivante en
 * {@code OUVERTURE} (RG-DOS-04). Exiger que l'antécédent soit clos aurait donc bloqué le parcours
 * normal juste après le recours, l'instance n'étant jamais close à ce moment-là.
 *
 * <p>Ce qui reste vrai, et que celle-ci retient : <b>on ne fait pas appel avant que le juge ait
 * statué</b>. Une étape antérieure {@code EN_DELIBERE} et un degré supérieur {@code EN_COURS}
 * décrivent un dossier simultanément en attente de jugement et déjà porté plus haut — un état qui
 * n'existe pas en procédure. Le cas s'atteint par positionnement direct : créer un appel sur un
 * dossier dont l'instance est en délibéré, puis en ouvrir les débats.
 *
 * <p><b>Les deux cassations sont concurrentes</b> : le pourvoi CCJA a pour antécédents l'instance et
 * l'appel, <b>jamais</b> la Cour Suprême. Un pourvoi national en délibéré n'empêche donc pas
 * d'ouvrir les débats devant la CCJA, et réciproquement. La table est celle de
 * {@code etapesPrecedentes}, écrite ici plutôt que déduite d'un rang — un calcul par ordre aurait
 * fait de RECOURS_2 un antécédent de RECOURS_2_CCJA.
 */
@Component
@RequiredArgsConstructor
public class AucuneEtapeAnterieureEnDelibere implements ConditionTransitionEtape {

    private final EtapeProcedureRepository etapeProcedureRepository;

    @Override
    public boolean concerne(StatutCycleVie de, StatutCycleVie vers) {
        // Seule l'ouverture des débats est visée. La reprise après recours part de DELIBERE_VIDE et
        // porte sur l'étape elle-même : la soumettre à cette règle n'aurait aucun sens.
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

    /** Miroir de {@code DossierService.etapesPrecedentes} : les degrés qui précèdent celui-ci. */
    private static List<TypeEtape> antecedents(TypeEtape type) {
        return switch (type) {
            case INSTANCE -> List.of();
            case RECOURS_1 -> List.of(TypeEtape.INSTANCE);
            case RECOURS_2, RECOURS_2_CCJA -> List.of(TypeEtape.INSTANCE, TypeEtape.RECOURS_1);
        };
    }

    /** Le nom que l'utilisateur lit à l'écran, et non la valeur technique de l'énuméré. */
    private static String libelle(TypeEtape type) {
        return switch (type) {
            case INSTANCE -> "Première instance";
            case RECOURS_1 -> "Appel";
            case RECOURS_2 -> "Pourvoi en cassation";
            case RECOURS_2_CCJA -> "Pourvoi CCJA";
        };
    }
}
