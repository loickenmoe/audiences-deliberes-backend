package com.afriland.audiences.portail.web.dto;

import com.afriland.audiences.ged.domain.FormatDocument;

import java.time.LocalDateTime;

/**
 * A case-file document as the lawyer sees it. Neither the uploader nor the storage path is exposed:
 * enough to recognise the document without revealing the bank's internal organisation.
 */
public record PieceAvocatResponse(
        Long id,
        String nomFichier,
        String typeDocument,
        FormatDocument format,
        Long poids,
        LocalDateTime partageLe) {
}
