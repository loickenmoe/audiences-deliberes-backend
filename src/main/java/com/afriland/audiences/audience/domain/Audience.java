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

    /**
     * Ce que le juriste veut dire à l'avocat de cette audience (QF-64). **Distincte du compte
     * rendu**, qui reste interne : il peut juger la stratégie, voire le travail de l'avocat
     * lui-même (RG-INT-07). L'avocat ne la lit que s'il est constitué.
     */
    @Column(name = "note_avocat")
    private String noteAvocat;

    /**
     * La juridiction devant laquelle l'audience se tient (V34).
     *
     * <p><b>Distincte de {@code dossier.juridictionSaisie}</b>, qui est celle de la saisine — le
     * premier degré. Une audience d'appel ne se tient pas devant le tribunal qui a jugé : afficher
     * la juridiction du dossier pour toutes les audiences ne laissait pas l'information manquante,
     * elle la rendait <b>fausse</b>.
     *
     * <p>Héritée du dossier à la planification quand l'utilisateur n'en indique pas d'autre : c'est
     * le cas courant, et c'est ce qui était affiché jusqu'ici.
     */
    @Size(max = 100)
    @Column(length = 100)
    private String juridiction;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatutAudience statut = StatutAudience.PLANIFIEE;

    /** Q-76 : motif obligatoire d'une annulation (renvoi, date erronée). */
    @Column(name = "motif_annulation")
    private String motifAnnulation;

    @Column(name = "date_annulation")
    private LocalDateTime dateAnnulation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
