package com.afriland.audiences.frais.domain;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.referentiel.domain.Avocat;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fee request filed by a lawyer assigned to a case (Epic 4, RG-INT-01/03/04/05).
 * Workflow: filing (lawyer) → compliance (assistant) → appropriateness (DJA) → joint approval
 * DJ+DJA if the case is sensitive or the amount &gt; applicable threshold → payment (assistant).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "demande_frais")
public class DemandeFrais {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avocat_id", nullable = false)
    private Avocat avocat;

    @NotNull
    @Column(nullable = false)
    private Double montant;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "pieces_justificatives", columnDefinition = "text[]")
    private List<String> piecesJustificatives;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_circuit", nullable = false, length = 30)
    private StatutCircuitFrais statutCircuit = StatutCircuitFrais.DEPOSEE;

    @Column(name = "motif_rejet")
    private String motifRejet;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_paiement", length = 20)
    private StatutPaiementFrais statutPaiement = StatutPaiementFrais.EN_ATTENTE;

    @Column(name = "reference_facture", length = 50, unique = true)
    private String referenceFacture;

    /** CONF04: last reminder for the joint approval, null until one has been sent. */
    @Column(name = "date_derniere_relance")
    private LocalDateTime dateDerniereRelance;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
