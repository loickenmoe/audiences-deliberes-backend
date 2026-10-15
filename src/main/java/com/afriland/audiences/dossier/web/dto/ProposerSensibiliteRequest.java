package com.afriland.audiences.dossier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Proposition de diligence particulière sur un dossier déjà ouvert (métier, 2026-10-03,
 * observation 5).
 *
 * <p>Une affaire se révèle sensible en cours de route : une contrepartie change de nature, un
 * montant se découvre, la presse s'en saisit. Jusqu'ici la diligence ne se posait qu'à la création,
 * et le dossier ouvert comme ordinaire le restait définitivement.
 *
 * <p><b>Mêmes exigences qu'à la création</b> : seuil et type de client sont obligatoires, car c'est
 * la proposition du juriste que le DJ ou la DJA arbitrera (RG-DOS-09). Le {@code motif}, lui, n'est
 * pas demandé à la création : là, la diligence est annoncée dès l'ouverture. Ici, le dossier vivait
 * sans elle - ce qui a changé doit être dit.
 */
public record ProposerSensibiliteRequest(
        @NotNull @PositiveOrZero Double seuilMontant,
        @NotBlank @Size(max = 50) String typeClientSensible,
        @NotBlank @Size(max = 255) String motif) {
}
