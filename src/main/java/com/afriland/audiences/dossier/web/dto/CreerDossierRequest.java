package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.referentiel.domain.CategorieDossier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Case creation request (FR-DOS-01, US 1.1).
 * <p>
 * <b>The reference is not part of this request</b>: the system assigns it. It is absent rather than
 * ignored, so that the contract does not suggest it is used.
 * <p>
 * {@code categorie} is optional: derived from {@code nature} via {@code nature_dossier}
 * (RG-DOS-02); if provided it must match, otherwise 400.
 * <p>
 * {@code dossierCreditOrigine}, {@code pvTransfert}, {@code incidentCompteReference} and
 * {@code elementsJustificatifs} are <b>optional</b>: the attachments are mandatory, not their text.
 * <p>
 * {@code seuilMontant}/{@code typeClientSensible} only matter when {@code estSensible} is true:
 * they are the in-house lawyer's <b>proposal</b>, subject to DJ/DJA validation (RG-DOS-09), and
 * never written directly to {@code dossier.seuil_montant}.
 */
public record CreerDossierRequest(
        @NotBlank @Size(max = 255) String nature,
        CategorieDossier categorie,
        @NotBlank @Size(max = 100) String juridictionSaisie,
        @NotNull Long clientId,
        @NotNull @Valid PartiesDto parties,
        @NotNull @PositiveOrZero Double risqueEncouru,
        String recoursExerces,
        @Size(max = 255) String motifRenvoi,
        // Mandatory; existence is checked by the service, which alone can query the reference data.
        @NotBlank @Size(max = 20) String codeAgenceOrigine,
        @Size(max = 20) String codeAgenceContentieuse,
        @Size(max = 50) String dossierCreditOrigine,
        @Size(max = 255) String pvTransfert,
        @Size(max = 50) String incidentCompteReference,
        String elementsJustificatifs,
        String champAlarme,
        Boolean estSensible,
        @PositiveOrZero Double seuilMontant,
        @Size(max = 50) String typeClientSensible,
        @NotEmpty List<Long> juristesAffectes,
        @NotEmpty List<Long> avocatsAffectes) {
}
