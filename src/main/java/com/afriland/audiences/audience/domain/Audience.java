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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audience planifiée sur une étape de procédure (RG-AUD-01). Le module {@code audience} dépend
 * de {@code dossier} (cf. FUNCTIONAL_MAP.md §2), une {@code @ManyToOne} directe est donc légitime
 * ici — contrairement à {@code alerte}/{@code historique_action}, qui restent un socle
 * transversal sans dépendance vers les modules métier.
 * Cf. dictionnaire des données §2.3.1 (table {@code audience}).
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

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatutAudience statut = StatutAudience.PLANIFIEE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
