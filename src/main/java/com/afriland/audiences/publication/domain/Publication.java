package com.afriland.audiences.publication.domain;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Publication déposée par un avocat sur un dossier : {@link CompteRenduAudience} (US 5.1) ou
 * {@link AutrePublication} (US 5.3) — hiérarchie {@code JOINED} identique à
 * {@code Intervenant}/{@code Avocat} (référentiel, M2). Le module {@code publication} dépend de
 * {@code dossier}, {@code audience} (rattachement du compte rendu) et {@code ged} (stockage des
 * fichiers d'{@code AutrePublication}) — cf. FUNCTIONAL_MAP.md §5.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "publication")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avocat_id", nullable = false)
    private Avocat avocat;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @CreationTimestamp
    @Column(name = "date_depot", updatable = false)
    private LocalDateTime dateDepot;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_validation", length = 20)
    private StatutPublication statutValidation = StatutPublication.DEPOSE;

    @Column(name = "motif_rejet")
    private String motifRejet;

    /**
     * Restriction d'accès optionnelle (US 5.4) : {@code null} = consultable par tout profil interne
     * une fois {@code VALIDE} ; sinon, seul cet utilisateur peut la consulter (403/ERR-009 sinon).
     * Renseignée par l'avocat au dépôt — aucun endpoint dédié ne la modifie a posteriori.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restriction_acces")
    private Utilisateur restrictionAcces;
}
