package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A produced and sealed audit archive. The file is stored in MinIO and this row records its SHA-256
 * hash with the period, author and timestamp, so the file can later be proven authentic. The period
 * is stored so the hash also proves what the file covers, not just its integrity.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "export_audit")
public class ExportAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @CreationTimestamp
    @Column(name = "date_export", updatable = false)
    private LocalDateTime dateExport;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perimetre perimetre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FormatExportAudit format;

    /** Number of lines actually written. */
    @Column(nullable = false)
    private long lignes;

    /** Hex SHA-256 of the stored bytes; makes the archive verifiable. */
    @Column(nullable = false, length = 64)
    private String empreinte;

    @Column(name = "taille_octets", nullable = false)
    private long tailleOctets;

    @Column(name = "cle_stockage", nullable = false)
    private String cleStockage;

    /**
     * Human-readable reference such as {@code AUD-2026-0042}: the hash proves, the reference names.
     * The number comes from a global sequence, not a yearly counter, which would require a lock.
     */
    @Column(nullable = false, length = 20)
    private String reference;
}
