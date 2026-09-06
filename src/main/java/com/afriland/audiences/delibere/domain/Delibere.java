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
 * Délibéré rendu sur une étape {@code EN_DELIBERE} (RG-DEL-01, FR-DEL-01). Le module
 * {@code delibere} dépend de {@code dossier} et {@code audience} (cf. FUNCTIONAL_MAP.md §3), une
 * {@code @ManyToOne} directe vers {@code Dossier}/{@code EtapeProcedure} est donc légitime, sur
 * le même principe que le module {@code audience} (M6).
 * <p>
 * {@code dateEcheanceRecours} (Q-19/Q-42, DECISIONS_AND_OPEN_QUESTIONS.md) n'existe que pour un
 * résultat {@code DEFAVORABLE}/{@code MIXTE} — seuls ceux-ci ouvrent une voie de recours ; elle
 * est distincte du mécanisme générique d'alarme (Q-04) car propre au cycle de vie du délibéré et
 * suivie par un planificateur dédié ({@code DelaiRecoursScheduler}).
 * <p>
 * Une étape peut porter plusieurs délibérés au fil du temps (rabattements successifs, RG-DEL-07) :
 * pas de contrainte d'unicité sur {@code etape_id}. Cf. dictionnaire des données §2.3.3.
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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ResultatDelibere resultat;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_expedition", length = 20)
    private StatutExpedition statutExpedition = StatutExpedition.A_LEVER;

    @Column(name = "date_echeance_recours")
    private LocalDateTime dateEcheanceRecours;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
