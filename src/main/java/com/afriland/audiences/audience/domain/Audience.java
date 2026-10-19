package com.afriland.audiences.audience.domain;

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
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Hearing scheduled on a procedure step (RG-AUD-01). The {@code audience} module depends on
 * {@code dossier}, so a direct {@code @ManyToOne} is legitimate here, unlike the cross-cutting
 * {@code alerte} and {@code historique_action}.
 * See data dictionary §2.3.1 (table {@code audience}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audience")
public class Audience {

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
    @Column(name = "date_planifiee", nullable = false)
    private LocalDate datePlanifiee;

    @Column(name = "date_tenue")
    private LocalDate dateTenue;

    @Column(name = "compte_rendu")
    private String compteRendu;

    /**
     * What the in-house lawyer wants to tell the assigned lawyer about this hearing. <b>Separate
     * from the report</b>, which stays internal (RG-INT-07). The lawyer only sees it when assigned
     * to the case.
     */
    @Column(name = "note_avocat")
    private String noteAvocat;

    /**
     * The court where the hearing takes place.
     * <p><b>Distinct from {@code dossier.juridictionSaisie}</b>, the first-instance court: an
     * appeal hearing is not held before the court that ruled. Defaults to the case's court when
     * none is given at scheduling.
     */
    @Size(max = 100)
    @Column(length = 100)
    private String juridiction;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatutAudience statut = StatutAudience.PLANIFIEE;

    /** Mandatory reason for a cancellation (adjournment, wrong date). */
    @Column(name = "motif_annulation")
    private String motifAnnulation;

    @Column(name = "date_annulation")
    private LocalDateTime dateAnnulation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
