package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.StatutPartageAvocat;

import java.time.LocalDateTime;

/**
 * A document in the DJ/DJA queue of shares to approve. Named so the DJ knows which case file and
 * document are concerned, and who proposes it, without opening three screens.
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
