package com.afriland.audiences.common.audit;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Trace de toute action réalisée sur un dossier (RG-DOS-06). {@code dossierId} est une référence
 * libre (pas de {@code @ManyToOne}) : le module {@code common} ne dépend pas du module
 * {@code dossier} (introduit en M4), pour éviter tout couplage inverse. Cf. dictionnaire des
 * données §2.4.4 (table {@code historique_action}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "historique_action")
public class HistoriqueAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dossier_id")
    private Long dossierId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /**
     * Nature typée de l'action (Q-111), sur laquelle portent les filtres d'audit. C'est elle qui
     * fait foi ; {@link #action} n'en est que la formulation lisible.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TypeActionHistorique code;

    /**
     * Phrase française dérivée de {@link #code} au moment de l'écriture. Conservée en base pour que
     * l'affichage existant ne change pas, et parce qu'une ligne d'audit doit rester lisible sans
     * détenir l'énumération qui l'a produite. Non traduisible en l'état (QF-24).
     */
    @NotBlank
    @Column(nullable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @CreationTimestamp
    @Column(name = "date_action", updatable = false)
    private LocalDateTime dateAction;
}
