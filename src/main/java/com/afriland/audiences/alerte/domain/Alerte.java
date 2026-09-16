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
 * Notification déclenchée par un événement métier (RG-ALR-01/02/03). {@code dossierId} est une
 * référence libre (pas de {@code @ManyToOne}), sur le même principe que
 * {@link com.afriland.audiences.common.audit.HistoriqueAction#getDossierId()} : le module
 * {@code alerte} est un socle transversal (dépendance de 8 modules, cf. FUNCTIONAL_MAP.md §7) et
 * ne doit pas dépendre en retour du module {@code dossier} — de plus, certaines alertes (ex.
 * {@code CHARGE_MAX_AVOCAT}) ne sont rattachées à aucun dossier ({@code dossier_id} est
 * nullable en base).
 * Cf. dictionnaire des données §2.5.1 (table {@code alerte}).
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
     * Issue de la diffusion temps réel (Q-109). <b>Nullable à dessein</b> : les alertes antérieures
     * à la migration V16 n'ont pas été observées, et le taux de délivrance ne porte que sur ce qui
     * a été réellement mesuré. Les compter d'office comme réussies ou échouées reviendrait à
     * inventer le chiffre que l'on cherche à établir.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_diffusion", length = 20)
    private StatutDiffusion statutDiffusion;

    /** Instant où l'issue a été observée — succès comme échec. */
    @Column(name = "date_diffusion")
    private LocalDateTime dateDiffusion;
}
