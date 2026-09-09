package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.referentiel.domain.CategorieDossier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requête de création d'un dossier (FR-DOS-01, US 1.1).
 * <p>
 * {@code categorie} est optionnelle : dérivée du référentiel {@code nature_dossier} à partir de
 * {@code nature} (RG-DOS-02) ; si fournie, elle doit être cohérente avec cette dérivation, sinon
 * 400 (cf. Q-11 — la cardinalité ET/OU des champs conditionnels par catégorie est tranchée ici en
 * ET, conformément aux user stories, plus strictement que la contrainte SQL qui reste en OU).
 * <p>
 * {@code seuilMontant}/{@code typeClientSensible} ne sont pertinents que si {@code estSensible} est
 * vrai : ils constituent la <b>proposition</b> du juriste, soumise à validation DJ/DJA
 * (RG-DOS-09) — ils ne sont jamais écrits directement dans {@code dossier.seuil_montant}
 * (cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-10, Q-26).
 */
public record CreerDossierRequest(
        @NotBlank @Size(max = 50) String reference,
        @NotBlank @Size(max = 255) String nature,
        CategorieDossier categorie,
        @NotBlank @Size(max = 100) String juridictionSaisie,
        @NotNull Long clientId,
        PartiesDto parties,
        @NotNull @PositiveOrZero Double risqueEncouru,
        String recoursExerces,
        @Size(max = 255) String motifRenvoi,
        @Size(max = 20) String codeAgenceOrigine,
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
