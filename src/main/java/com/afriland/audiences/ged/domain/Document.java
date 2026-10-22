package com.afriland.audiences.ged.domain;

import com.afriland.audiences.dossier.domain.Dossier;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Document uploaded to the GED (RG-GED-02/04/06/08) and stored in MinIO; {@code cheminStockage}
 * holds the object key. The {@code ged} module depends on {@code dossier}, so a direct
 * {@code @ManyToOne} is legitimate.
 * <p>
 * {@code typeDocument} is free text (RG-GED defines no taxonomy), unlike
 * {@code autre_publication.type_document}, which is restricted to {PIECE, DECISION, CONCLUSION}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @NotBlank
    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @NotBlank
    @Column(name = "type_document", nullable = false, length = 50)
    private String typeDocument;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private FormatDocument format;

    @NotNull
    @Column(nullable = false)
    private Long poids;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_chargement", nullable = false)
    private Utilisateur auteurChargement;

    @CreationTimestamp
    @Column(name = "date_chargement", updatable = false)
    private LocalDateTime dateChargement;

    @NotNull
    @Builder.Default
    @Column(name = "est_supprime")
    private Boolean estSupprime = false;

    @NotBlank
    @Column(name = "chemin_stockage", nullable = false)
    private String cheminStockage;

    /**
     * Sharing with the case file's lawyer. Defaults to {@code NON}: the GED holds the original credit
     * file, the transfer report and the client's financial documents, all covered by banking secrecy.
     * A document only reaches the lawyer if someone decided so.
     */
    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "partage_avocat", length = 20, nullable = false)
    private StatutPartageAvocat partageAvocat = StatutPartageAvocat.NON;

    /** Who shared the document, or proposed sharing it on a sensitive case file. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partage_par")
    private Utilisateur partagePar;

    @Column(name = "partage_le")
    private LocalDateTime partageLe;

    /** The DJ or DJA who made the document visible on a sensitive case file. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partage_valide_par")
    private Utilisateur partageValidePar;

    @Column(name = "partage_valide_le")
    private LocalDateTime partageValideLe;
}
