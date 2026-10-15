package com.afriland.audiences.portail.web.dto;

import com.afriland.audiences.ged.domain.FormatDocument;

import java.time.LocalDateTime;

/**
 * Une pièce du dossier telle que l'avocat la voit (QF-64). Ni l'auteur du chargement, ni le
 * chemin de stockage : il lui faut de quoi reconnaître la pièce, pas de quoi lire l'organisation
 * interne de la banque.
 */
public record PieceAvocatResponse(
        Long id,
        String nomFichier,
        String typeDocument,
        FormatDocument format,
        Long poids,
        LocalDateTime partageLe) {
}
