package com.afriland.audiences.intervenant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Ce qu'un rédacteur enregistre sur sa lettre (Q-115 B2).
 *
 * <p><b>Peu de champs sont obligatoires, et c'est délibéré.</b> Le gabarit de la direction juridique
 * est fait pour être complété — à la main, le cas échéant : exiger le débiteur ou le montant à la
 * saisie interdirait une pratique existante. Ce qui manque rend des pointillés.
 *
 * <p>Restent obligatoires les trois éléments sans lesquels le document n'est plus une lettre de la
 * banque : le suffixe du numéro, l'objet et le signataire.
 *
 * @param numero un numéro imposé par le rédacteur ; laissé vide, il est tiré de la séquence à la
 *               signature. Un numéro déjà employé est refusé — la série ne doit pas se répéter
 */
public record RedigerLettreRequest(
        @Positive Integer numero,
        @NotBlank @Size(max = 160) String suffixeNumero,
        @NotBlank @Size(max = 80) String lieu,
        @Size(max = 160) String destinataireNom,
        @Size(max = 120) String destinataireQualite,
        @Size(max = 80) String destinataireVille,
        @Size(max = 255) String concerne,
        @NotBlank @Size(max = 255) String objet,
        @NotBlank String corps,
        @NotBlank @Size(max = 160) String signataire) {
}
