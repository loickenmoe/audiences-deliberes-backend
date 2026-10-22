package com.afriland.audiences.alerte.domain;

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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Alert raised by a business event (RG-ALR-01/02/03). {@code dossierId} is a plain reference, not a
 * {@code @ManyToOne}: the {@code alerte} module is a cross-cutting foundation and must not depend
 * on {@code dossier}. Some alerts (e.g. {@code CHARGE_MAX_AVOCAT}) have no case at all (
 * {@code dossier_id} is nullable).
 * See data dictionary §2.5.1 (table {@code alerte}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "alerte")
public class Alerte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dossier_id")
    private Long dossierId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private Utilisateur destinataire;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TypeAlerte type;

    @NotNull
    @Column(nullable = false)
    private String declencheur;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalAlerte canal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(length = 20)
    private StatutAlerte statut = StatutAlerte.DECLENCHEE;

    @CreationTimestamp
    @Column(name = "date_declenchement", updatable = false)
    private LocalDateTime dateDeclenchement;

    @Column(name = "date_traitement")
    private LocalDateTime dateTraitement;

    /**
     * Outcome of the real-time delivery. <b>Nullable on purpose</b>: alerts created before
     * migration V16 were never observed, and the delivery rate only covers what was actually
     * measured.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_diffusion", length = 20)
    private StatutDiffusion statutDiffusion;

    /** When the outcome was observed, success or failure. */
    @Column(name = "date_diffusion")
    private LocalDateTime dateDiffusion;
}
