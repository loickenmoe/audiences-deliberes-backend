package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;

import java.util.Optional;

/**
 * A <b>factual</b> condition required for a step to change status. {@link EtapeTransitionValidator}
 * only says which status may follow which; this says what must have happened.
 * <p>An interface because these facts live in {@code audience}, {@code delibere}, {@code decision}
 * and {@code frais}, which depend on {@code dossier}: {@code dossier} publishes the contract, each
 * module provides its conditions, and Spring collects them. Adding a rule means adding a class in
 * the relevant module.
 */
public interface ConditionTransitionEtape {

    /** Whether this condition applies to this transition. */
    boolean concerne(StatutCycleVie de, StatutCycleVie vers);

    /**
     * What prevents the transition, if anything. A reason rather than a boolean, displayed as is on
     * screen.
     *
     * @return the refusal reason, or {@link Optional#empty()} if the condition is met
     */
    Optional<String> empechement(EtapeProcedure etape);
}
