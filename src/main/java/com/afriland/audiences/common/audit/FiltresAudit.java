package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;

import java.time.LocalDate;

/**
 * Criteria of an audit consultation or extraction, as received; {@code null} means "no filter".
 *
 * @param utilisateurId author of the searched actions, not the consulting user
 */
public record FiltresAudit(Perimetre perimetre, Long dossierId, Long utilisateurId,
                           TypeActionHistorique code, LocalDate dateDebut, LocalDate dateFin) {
}
