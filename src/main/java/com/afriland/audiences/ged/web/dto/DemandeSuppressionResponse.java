package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.StatutSuppression;

import java.time.LocalDateTime;

/**
 * Q-79 : la demande nomme ce qu'elle vise — fichier, type, dossier. Sans cela, la file du DJ/DJA
 * n'affichait qu'un identifiant de document, et l'historique d'une demande approuvée restait
 * anonyme (le document supprimé répond 404 à la consultation).
 */
public record DemandeSuppressionResponse(
        Long id,
        Long documentId,
        String documentNom,
        String typeDocument,
        Long dossierId,
        String referenceDossier,
        Long demandeurId,
        String motif,
        StatutSuppression statut,
        Long valideParId,
        String motifRejet,
        LocalDateTime dateDemande,
        LocalDateTime dateDecision,
        LocalDateTime dateDerniereRelance) {
}
