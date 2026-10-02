package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;

import java.util.Optional;

/**
 * Une condition <b>de fait</b> à remplir pour qu'une étape change de statut (métier, 2026-09-29).
 *
 * <p><b>Le manque que cela comble.</b> {@link EtapeTransitionValidator} dit quel statut peut suivre
 * quel autre ; il ne dit rien de ce qui doit avoir eu lieu. On pouvait donc mettre en délibéré une
 * étape sans aucune audience, exercer un recours contre un jugement gagné, ou archiver un dossier
 * dont les frais n'étaient pas payés. L'ordre était juste, les faits absents.
 *
 * <p><b>Pourquoi une interface, et pas des appels directs.</b> Ces faits vivent dans
 * {@code audience}, {@code delibere}, {@code decision} et {@code frais} — quatre modules qui
 * dépendent <b>de</b> {@code dossier}. Les interroger d'ici créerait un cycle, celui-là même que
 * Q-67 a refusé pour la GED. Le sens de la dépendance est donc inversé : {@code dossier} publie ce
 * contrat, chaque module apporte ses propres conditions, et Spring les rassemble. Chacun garde la
 * connaissance de ce qu'il sait — {@code audience} seul sait ce qu'est une audience tenue.
 *
 * <p>Ajouter une règle demain, c'est ajouter une classe dans le module concerné : rien à modifier
 * ici, ni dans {@code DossierService}.
 */
public interface ConditionTransitionEtape {

    /** Cette condition a-t-elle son mot à dire sur ce passage précis ? */
    boolean concerne(StatutCycleVie de, StatutCycleVie vers);

    /**
     * Ce qui empêche le passage, s'il y a lieu.
     *
     * <p>Un motif, et non un booléen : refuser sans dire pourquoi laisserait l'utilisateur devant un
     * bouton qui ne marche pas. La phrase rendue est destinée à être lue telle quelle à l'écran.
     *
     * @return le motif du refus, ou {@link Optional#empty()} si la condition est remplie
     */
    Optional<String> empechement(EtapeProcedure etape);
}
