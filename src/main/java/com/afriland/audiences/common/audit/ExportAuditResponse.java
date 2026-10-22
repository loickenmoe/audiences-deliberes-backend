package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * API view of an audit archive. The storage key is deliberately omitted: it would only allow
 * bypassing the download endpoint, the only place where downloads are recorded.
 *
 * @param reference human-readable reference ({@code AUD-2026-0042}) naming the archive
 * @param empreinte hex SHA-256 of the stored file, proving its authenticity
 */
public record ExportAuditResponse(
        Long id,
        String reference,
        Long utilisateurId,
        LocalDateTime dateExport,
        LocalDate dateDebut,
        LocalDate dateFin,
        Perimetre perimetre,
        FormatExportAudit format,
        long lignes,
        String empreinte,
        long tailleOctets) {
}
