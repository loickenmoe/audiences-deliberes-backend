package com.afriland.audiences.portail.web.dto;

/**
 * Un dossier tel que l'avocat qui en a la charge peut le voir (Q-96).
 *
 * <p><b>Volontairement réduit à l'identification.</b> L'avocat est un prestataire <b>externe</b> :
 * il lui faut de quoi reconnaître et désigner son dossier au moment de déposer une demande de frais
 * (US 4.1) ou un compte rendu (US 5.1), rien de plus. Ni le client, ni le risque encouru, ni la
 * sensibilité, ni les juristes affectés — exposer le portefeuille de la banque à un tiers le rendrait
 * extractible en masse. Arbitrage du porteur du projet, 2026-09-14.
 */
public record DossierAvocatResponse(
        Long id,
        String reference,
        String nature,
        String juridictionSaisie) {
}
