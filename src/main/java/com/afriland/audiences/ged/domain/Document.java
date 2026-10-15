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
 * Document chargé dans la GED (RG-GED-02/04/06/08), stocké dans MinIO — {@code cheminStockage}
 * porte la clé d'objet (Q-07). Le module {@code ged} dépend de {@code dossier} (cf.
 * FUNCTIONAL_MAP.md §6), une {@code @ManyToOne} directe est donc légitime, même principe que
 * {@code audience}/{@code delibere}.
 * <p>
 * {@code typeDocument} reste un texte libre (RG-GED n'expose aucune taxonomie dédiée, contrairement
 * à {@code autre_publication.type_document} qui, lui, est fermé à {PIECE, DECISION, CONCLUSION} —
 * cf. dictionnaire des données §2.3.1/§2.4.2, à ne pas confondre).
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
     * Le partage avec l'avocat du dossier (QF-64). {@code NON} par défaut : la GED contient le
     * dossier crédit d'origine, le PV de transfert et des pièces financières du client - du secret
     * bancaire. Une pièce ne part chez l'avocat que si quelqu'un l'a décidé.
     */
    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "partage_avocat", length = 20, nullable = false)
    private StatutPartageAvocat partageAvocat = StatutPartageAvocat.NON;

    /** Qui a partagé la pièce, ou proposé de la partager sur un dossier sensible. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partage_par")
    private Utilisateur partagePar;

    @Column(name = "partage_le")
    private LocalDateTime partageLe;

    /** Le DJ ou la DJA qui a rendu la pièce visible sur un dossier sensible. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partage_valide_par")
    private Utilisateur partageValidePar;

    @Column(name = "partage_valide_le")
    private LocalDateTime partageValideLe;
}
