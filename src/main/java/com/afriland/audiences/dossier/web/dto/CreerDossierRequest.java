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
 * Requête de création d'un dossier (FR-DOS-01, US 1.1).
 * <p>
 * <b>La référence n'est pas dans cette requête</b> : le système l'attribue (métier, 2026-09-29 —
 * « les champs Référence doivent être automatiquement remplis par le système »). Elle est absente
 * du corps plutôt qu'ignorée, comme {@code ModifierClientRequest} le fait depuis Q-116 : un champ
 * qu'on accepte sans l'utiliser laisse croire qu'il sert. Un client qui l'enverrait malgré tout la
 * verrait simplement ignorée par la désérialisation.
 * <p>
 * {@code categorie} est optionnelle : dérivée du référentiel {@code nature_dossier} à partir de
 * {@code nature} (RG-DOS-02) ; si fournie, elle doit être cohérente avec cette dérivation, sinon
 * 400.
 * <p>
 * {@code dossierCreditOrigine}, {@code pvTransfert}, {@code incidentCompteReference} et
 * {@code elementsJustificatifs} sont <b>facultatifs depuis le 2026-09-29</b> (amendement de Q-11).
 * Le métier a déplacé l'obligation du texte vers la <b>pièce jointe</b> : « ce sont les pièces
 * jointes qui sont obligatoires, pas leur texte associé ». Ces références restent utiles — elles
 * nomment la pièce et servent à retrouver un dossier — mais ne conditionnent plus la création. US
 * 1.1 et US 1.5, qui les exigeaient, sont amendées d'autant ; la contrainte SQL
 * {@code chk_categorie_coherence} a été levée en V35 pour la même raison.
 * <p>
 * {@code seuilMontant}/{@code typeClientSensible} ne sont pertinents que si {@code estSensible} est
 * vrai : ils constituent la <b>proposition</b> du juriste, soumise à validation DJ/DJA
 * (RG-DOS-09) — ils ne sont jamais écrits directement dans {@code dossier.seuil_montant}
 * (cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-10, Q-26).
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
