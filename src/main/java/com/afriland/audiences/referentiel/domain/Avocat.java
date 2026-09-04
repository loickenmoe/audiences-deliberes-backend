package com.afriland.audiences.referentiel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Avocat partenaire : intervenant externe, seul type de prestataire disposant d'un compte
 * Keycloak (RG-INT-01) — dépôt de demandes de frais, de comptes rendus d'audience, réception des
 * lettres de constitution. Cf. dictionnaire des données §2.2.2 (table {@code avocat}).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "avocat")
@DiscriminatorValue("AVOCAT")
@PrimaryKeyJoinColumn(name = "id")
public class Avocat extends Intervenant {

    @NotBlank
    @Size(max = 255)
    @Column(name = "compte_keycloak", nullable = false, unique = true)
    private String compteKeycloak;

    @Builder.Default
    private Double indicateurCharge = 0d;

    @Builder.Default
    private Double indicateurPerformance = 0d;
}
