package com.afriland.audiences.intervenant.web.dto;

import java.time.LocalDateTime;

/**
 * Appointment letter as read by the UI.
 *
 * @param numero          null while the letter is a draft; assigned at signature so that no number
 *                        is wasted on an abandoned request
 * @param numeroPropose   number the sequence would serve if none is imposed; an indication only,
 *                        never reserved (database uniqueness decides at signature)
 * @param suffixeNumero   rest of the number line, split on "/" by the UI
 * @param modifiable      false once the letter is generated; a correction produces a new letter
 * @param dateGeneration  freeze time; null while the letter is a draft
 * @param redigeParNom    author of the latest version (the line manager may correct it before
 *                        signing); null until someone has drafted it
 * @param redigeParProfil author's profile, so the UI can show who corrected the letter
 */
public record LettreConstitutionResponse(
        Long id,
        Long demandeId,
        Integer numero,
        Integer numeroPropose,
        String suffixeNumero,
        String lieu,
        String destinataireNom,
        String destinataireQualite,
        String destinataireVille,
        String concerne,
        String objet,
        String corps,
        String signataire,
        boolean modifiable,
        LocalDateTime dateGeneration,
        LocalDateTime dateModification,
        String redigeParNom,
        String redigeParProfil) {
}
