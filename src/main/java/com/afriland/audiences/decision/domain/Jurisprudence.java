package com.afriland.audiences.decision.domain;

import com.afriland.audiences.dossier.domain.Dossier;
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
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entrée de la base jurisprudentielle (US 8.3, RG-DEF-05) — archive indexée, indépendante de la
 * hiérarchie {@link DecisionDefinitive} (le dictionnaire de données la traite comme une table à
 * part entière, non un troisième sous-type). {@code dossier} est optionnel : une jurisprudence
 * peut documenter un précédent sans dossier interne associé (FR-DEF-03). {@code cheminStockage} :
 * colonne absente du schéma initial (Q-58), déposée via {@code MinioStorageService} sans ligne
 * `document` GED (même raisonnement que Q-56 pour {@code autre_publication}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jurisprudence")
public class Jurisprudence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id")
    private Dossier dossier;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_archive", nullable = false, length = 30)
    private TypeArchiveJurisprudence typeArchive;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "mots_cles", columnDefinition = "text[]")
    private List<String> motsCles;

    @NotBlank
    @Size(max = 50)
    @Column(name = "nature_decision", nullable = false, length = 50)
    private String natureDecision;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String juridiction;

    @NotNull
    @Column(name = "date_decision", nullable = false)
    private LocalDate dateDecision;

    @Column(name = "chemin_stockage")
    private String cheminStockage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
