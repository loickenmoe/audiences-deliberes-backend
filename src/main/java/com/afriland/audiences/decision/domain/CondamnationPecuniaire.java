package com.afriland.audiences.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Condamnation pécuniaire (US 8.2, RG-DEF-03/04) : statut de paiement suivi dans le temps. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "condamnation_pecuniaire")
@DiscriminatorValue("CONDAMNATION_PECUNIAIRE")
@PrimaryKeyJoinColumn(name = "id")
public class CondamnationPecuniaire extends DecisionDefinitive {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SensCondamnation sens;

    @NotBlank
    @Size(max = 50)
    @Column(name = "nature_paiement", nullable = false, length = 50)
    private String naturePaiement;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_paiement", length = 20)
    private StatutPaiementCondamnation statutPaiement = StatutPaiementCondamnation.EN_ATTENTE;
}
