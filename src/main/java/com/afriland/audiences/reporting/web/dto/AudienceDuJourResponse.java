package com.afriland.audiences.reporting.web.dto;

import com.afriland.audiences.audience.domain.StatutAudience;

import java.util.List;

/**
 * A hearing on a given day, with what has been reported about it.
 *
 * <p>Both reports are shown side by side: the legal officer's (entered on the hearing) and the
 * lawyer's (filed as a publication, then validated). A hearing may carry two, one or none. The
 * listed legal officers are those assigned to the case; the model has no per-hearing owner.
 *
 * @param juristesAffectes     ids of the case's legal officers; the UI resolves their names
 * @param compteRenduJuriste   text entered on the hearing, or {@code null}
 * @param compteRenduAvocat    lawyer's report once validated, or {@code null}; an unvalidated
 *                             filing is excluded since no legal officer can read it yet
 * @param avocatDuCompteRendu  lawyer who filed the report, when there is one
 * @param urlPieceJointeAvocat download URL of the attachment to the lawyer's report, or
 *                             {@code null}; the legal officer's report is text only
 */
public record AudienceDuJourResponse(
        Long audienceId,
        Long dossierId,
        String referenceDossier,
        String juridiction,
        String etape,
        StatutAudience statut,
        List<Long> juristesAffectes,
        String compteRenduJuriste,
        String compteRenduAvocat,
        Long avocatDuCompteRendu,
        String urlPieceJointeAvocat) {

    /** True when neither side has reported on this hearing. */
    public boolean sansAucunCompteRendu() {
        return compteRenduJuriste == null && compteRenduAvocat == null;
    }
}
