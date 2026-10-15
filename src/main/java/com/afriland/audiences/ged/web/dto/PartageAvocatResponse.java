package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.StatutPartageAvocat;

import java.time.LocalDateTime;

/**
 * Une pièce dans la file des partages à valider du DJ et de la DJA (QF-64). Nommée : le DJ doit
 * savoir de quel dossier et de quelle pièce il s'agit, et qui la propose, sans ouvrir trois écrans.
 */
public record PartageAvocatResponse(
        Long documentId,
        Long dossierId,
        String referenceDossier,
        String nomFichier,
        String typeDocument,
        StatutPartageAvocat partageAvocat,
        String partageParNom,
        LocalDateTime partageLe) {
}
