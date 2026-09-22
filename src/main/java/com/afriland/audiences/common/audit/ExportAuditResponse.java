package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Une archive d'audit, telle qu'elle se lit (Q-113).
 *
 * <p><b>La clé de stockage n'y figure pas</b>, et c'est délibéré : elle ne servirait qu'à contourner
 * l'endpoint de téléchargement, seul endroit où la récupération est tracée.
 *
 * @param reference référence lisible ({@code AUD-2026-0042}). Elle <b>désigne</b> l'archive, là où
 *                  l'empreinte la <b>prouve</b> — c'est elle qu'on écrit dans un courrier, et par
 *                  elle qu'on retrouve l'empreinte.
 * @param empreinte SHA-256 hexadécimal du fichier déposé. C'est ce qui permet de démontrer, des
 *                  années plus tard, que le fichier produit est celui qui a été scellé — et donc de
 *                  distinguer une archive d'une simple copie.
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
