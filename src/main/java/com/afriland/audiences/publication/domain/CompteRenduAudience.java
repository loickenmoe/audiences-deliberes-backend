package com.afriland.audiences.publication.domain;

import com.afriland.audiences.audience.domain.Audience;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Hearing report filed by the lawyer (US 5.1, RG-INT-09), distinct from the report the legal
 * officer enters directly on {@link Audience#getCompteRendu()} (FR-AUD-02): two independent
 * reports, one internal (legal officer), the other published by the lawyer and subject to the
 * Assistant's validation.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "compte_rendu_audience")
@DiscriminatorValue("CR_AUDIENCE")
@PrimaryKeyJoinColumn(name = "id")
public class CompteRenduAudience extends Publication {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audience_id", nullable = false)
    private Audience audience;

    @NotBlank
    @Column(nullable = false)
    private String contenu;

    /**
     * Optional attachment supporting the report (court record, hearing note).
     * {@code null} when the report stands on its own, which remains the nominal case of US 5.1.
     */
    @Size(max = 255)
    @Column(name = "chemin_stockage", length = 255)
    private String cheminStockage;
}
