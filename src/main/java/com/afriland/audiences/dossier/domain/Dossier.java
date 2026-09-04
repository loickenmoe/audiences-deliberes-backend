package com.afriland.audiences.dossier.domain;

import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.domain.Client;
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
import jakarta.validation.constraints.Size;
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

/**
 * Procédure judiciaire suivie par la Banque (RG-DOS-01). Porte 1 à 3 {@link EtapeProcedure}.
 * Cf. dictionnaire des données §2.1.2 et {@code V1__schema_initial.sql}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dossier")
public class Dossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String nature;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategorieDossier categorie;

    @NotBlank
    @Size(max = 100)
    @Column(name = "juridiction_saisie", nullable = false, length = 100)
    private String juridictionSaisie;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Parties parties;

    @Builder.Default
    @Column(name = "risque_encouru")
    private Double risqueEncouru = 0d;

    @Column(name = "recours_exerces")
    private String recoursExerces;

    @Size(max = 255)
    @Column(name = "motif_renvoi")
    private String motifRenvoi;

    @Size(max = 20)
    @Column(name = "code_agence_origine", length = 20)
    private String codeAgenceOrigine;

    @Size(max = 20)
    @Column(name = "code_agence_contentieuse", length = 20)
    private String codeAgenceContentieuse;

    @Size(max = 50)
    @Column(name = "dossier_credit_origine", length = 50)
    private String dossierCreditOrigine;

    @Size(max = 255)
    @Column(name = "pv_transfert")
    private String pvTransfert;

    @Size(max = 50)
    @Column(name = "incident_compte_reference", length = 50)
    private String incidentCompteReference;

    @Column(name = "elements_justificatifs")
    private String elementsJustificatifs;

    @Column(name = "champ_alarme")
    private String champAlarme;

    @Builder.Default
    @Column(name = "est_sensible")
    private Boolean estSensible = false;

    @Column(name = "seuil_montant")
    private Double seuilMontant;

    @Size(max = 50)
    @Column(name = "type_client_sensible", length = 50)
    private String typeClientSensible;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "seuil_origine", length = 20)
    private SeuilOrigine seuilOrigine = SeuilOrigine.GLOBAL;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "sensibilite_statut", length = 20)
    private StatutValidation sensibiliteStatut = StatutValidation.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensibilite_valide_par")
    private Utilisateur sensibiliteValidePar;

    @Size(max = 255)
    @Column(name = "sensibilite_motif_rejet")
    private String sensibiliteMotifRejet;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
