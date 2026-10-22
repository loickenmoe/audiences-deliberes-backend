package com.afriland.audiences.referentiel.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Non-user service provider (notary, bailiff, court clerk, land registrar): no Keycloak account,
 * may receive an email notification if needed (RG-INT-02).
 * See data dictionary §2.2.3 (table {@code autre_prestataire}).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "autre_prestataire")
@DiscriminatorValue("AUTRE_PRESTATAIRE")
@PrimaryKeyJoinColumn(name = "id")
public class AutrePrestataire extends Intervenant {

    @Builder.Default
    private Boolean notification = false;

    @Override
    public boolean accepteNotificationsExternes() {
        return Boolean.TRUE.equals(notification);
    }
}
