package com.afriland.audiences.referentiel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Global configuration parameter editable by the DJ (e.g. SEUIL_MONTANT_DEFAUT,
 * SEUIL_PROROGATIONS; see user stories §11.2, CONF01 to CONF07).
 * See data dictionary §2.4.3 (table {@code configuration_systeme}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "configuration_systeme")
public class ConfigurationSysteme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String cle;

    /**
     * May be empty for lists that allow it (notification settings); up to 1000 characters, to hold a
     * full grid of alert types.
     */
    @NotNull
    @Size(max = 1000)
    @Column(nullable = false, length = 1000)
    private String valeur;

    private String description;

    @NotBlank
    @Size(max = 50)
    @Column(name = "modifiable_par", nullable = false, length = 50)
    private String modifiablePar;

    @UpdateTimestamp
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    /**
     * Author of the last change.
     *
     * <p>A plain reference rather than a {@code @ManyToOne}: this field is a display hint, not a
     * business relation, and {@code historique_action} keeps the full trace of the change anyway.
     * Null if the parameter has never been changed, or if the author's account has since been deleted.
     */
    @Column(name = "dernier_auteur_id")
    private Long dernierAuteurId;
}
