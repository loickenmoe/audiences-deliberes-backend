package com.afriland.audiences.intervenant.domain;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.referentiel.domain.Intervenant;
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

import java.time.LocalDateTime;

/**
 * Request to appoint a provider (lawyer or other) on a case, subject to the line manager's second
 * signature (RG-INT-08, FR-INT-06/07). The provider is the polymorphic {@code Intervenant}; the
 * letter generated on validation is stored in the document management module.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "demande_constitution")
public class DemandeConstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prestataire_id", nullable = false)
    private Intervenant prestataire;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demandeur_id", nullable = false)
    private Utilisateur demandeur;

    @NotBlank
    @Column(nullable = false)
    private String motif;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatutConstitution statut = StatutConstitution.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valide_par")
    private Utilisateur validePar;

    @Column(name = "motif_rejet")
    private String motifRejet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lettre_document_id")
    private Document lettreDocument;

    @CreationTimestamp
    @Column(name = "date_demande", updatable = false)
    private LocalDateTime dateDemande;

    @Column(name = "date_decision")
    private LocalDateTime dateDecision;
}
