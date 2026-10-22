package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.StatutValidation;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.referentiel.domain.CategorieDossier;

import java.time.LocalDate;

/**
 * Optional criteria of {@code GET /dossiers}, as received from the client; {@code null} or blank
 * means "no filter".
 *
 * <p>"My cases" is not part of it: it is resolved from the authenticated user by
 * {@link DossierService}, never from a received value.
 *
 * @param typeEtape   keeps only cases with a step of this type
 * @param statutEtape keeps only cases with a step in this status
 * @param dateDebut   start of the hearing window, inclusive; {@code null} for no bound
 * @param dateFin     end of the hearing window, inclusive
 */
public record FiltresDossiers(String nature, CategorieDossier categorie,
                              String juridiction, String reference, Long clientId,
                              Boolean estSensible, StatutValidation sensibiliteStatut,
                              TypeEtape typeEtape, StatutCycleVie statutEtape,
                              LocalDate dateDebut, LocalDate dateFin) {
}
