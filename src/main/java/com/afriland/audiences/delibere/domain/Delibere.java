package com.afriland.audiences.delibere.domain;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Deliberation rendered on an {@code EN_DELIBERE} step (RG-DEL-01, FR-DEL-01).
 * <p>
 * {@code dateEcheanceRecours} exists only for a {@code DEFAVORABLE}/{@code MIXTE} result, the only
 * ones that open an appeal; it is tracked by {@code DelaiRecoursScheduler}, not by the generic alarms.
 * <p>
 * A step may carry several deliberations over time (successive reopenings, RG-DEL-07), hence no
 * uniqueness constraint on {@code etape_id}. Lifecycle: created without a result
 * ({@link EtatDelibere#EN_ATTENTE}), extended or reopened while pending, then delivered once the
 * result is known. At most one pending deliberation per step.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "delibere")
public class Delibere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etape_id")
    private EtapeProcedure etape;

    @NotNull
    @Column(name = "date_deliberee", nullable = false)
    private LocalDate dateDeliberee;

    /** Absent until the deliberation is delivered (while pending or extended). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ResultatDelibere resultat;

    /** Exists only once the decision is rendered; set to {@code A_LEVER} when the deliberation is delivered. */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_expedition", length = 20)
    private StatutExpedition statutExpedition;

    /** RG-DEL-07: the judge reopened the proceedings before ruling. */
    @Column(name = "date_rabattement")
    private LocalDate dateRabattement;

    @Column(name = "motif_rabattement")
    private String motifRabattement;

    @Column(name = "date_echeance_recours")
    private LocalDateTime dateEcheanceRecours;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
