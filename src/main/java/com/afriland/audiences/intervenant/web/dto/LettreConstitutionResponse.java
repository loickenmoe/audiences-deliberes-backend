package com.afriland.audiences.intervenant.web.dto;

import java.time.LocalDateTime;

/**
 * La lettre telle qu'un écran la lit (Q-115 B2).
 *
 * @param numero          nul tant que la lettre est un brouillon : il est attribué à la signature,
 *                        pour ne pas brûler un numéro sur une demande sans suite
 * @param numeroPropose   ce que la séquence servira si le rédacteur n'impose rien. Affiché en
 *                        <b>indication</b>, jamais réservé : deux rédacteurs peuvent voir le même,
 *                        et c'est l'unicité en base qui tranche à la signature
 * @param suffixeNumero   le reste de la ligne, découpé sur « / » par l'écran
 * @param modifiable      faux dès que la lettre est générée. Une pièce qu'on peut retoucher en
 *                        silence ne prouve rien : une correction donne une nouvelle lettre
 * @param dateGeneration  l'instant du gel ; nul tant que la lettre est un brouillon
 * @param redigeParNom    qui a écrit la dernière version. Le SH peut corriger avant de signer
 *                        (métier, 2026-10-06) : le juriste doit voir que sa lettre a bougé, et par
 *                        qui. Nul tant que personne n'a rédigé - le brouillon naît avec la demande
 * @param redigeParProfil le profil du rédacteur, pour que l'écran dise « corrigée par le SH » sans
 *                        avoir à deviner qui est qui
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
