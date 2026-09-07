package com.afriland.audiences.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Adjudication (US 8.1, RG-DEF-01/02) : décision définitive attribuant le bien saisi, avec
 * traitement comptable (reprise de provision, restitution de soulte, mutation, reliquat).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "adjudication")
@DiscriminatorValue("ADJUDICATION")
@PrimaryKeyJoinColumn(name = "id")
public class Adjudication extends DecisionDefinitive {

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String beneficiaire;

    @Size(max = 30)
    @Column(name = "statut_comptabilisation", length = 30)
    private String statutComptabilisation;

    @Builder.Default
    @Column(name = "reprise_provision")
    private Boolean repriseProvision = false;

    @Builder.Default
    @Column(name = "restitution_soulte")
    private Boolean restitutionSoulte = false;

    @Builder.Default
    private Boolean mutation = false;

    @PositiveOrZero
    @Builder.Default
    private Double reliquat = 0d;
}
