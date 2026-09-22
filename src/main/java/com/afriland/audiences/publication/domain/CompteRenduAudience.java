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
 * Compte rendu d'audience déposé par l'avocat (US 5.1, RG-INT-09) — distinct du compte rendu que
 * le juriste saisit directement sur {@link Audience#getCompteRendu()} (Épic 2, FR-AUD-02) : deux
 * comptes rendus indépendants, l'un interne (juriste), l'autre publié par l'avocat et soumis à
 * validation de l'Assistante.
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
     * Pièce jointe facultative (Q-98) : le document qui étaye le récit — plumitif, note d'audience.
     * {@code null} quand le compte rendu se suffit à lui-même, ce qui reste le cas nominal d'US 5.1.
     */
    @Size(max = 255)
    @Column(name = "chemin_stockage", length = 255)
    private String cheminStockage;
}
