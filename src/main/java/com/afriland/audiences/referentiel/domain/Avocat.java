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
 * Partner lawyer: external party, the only provider type with a Keycloak account (RG-INT-01), used
 * to file expense requests and hearing reports and to receive appointment letters.
 * See data dictionary §2.2.2 (table {@code avocat}).
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

    /** Consent to external channels (V29), checked by default. */
    @Builder.Default
    private Boolean notification = true;

    @Override
    public boolean accepteNotificationsExternes() {
        return Boolean.TRUE.equals(notification);
    }

    @Builder.Default
    private Double indicateurPerformance = 0d;
}
