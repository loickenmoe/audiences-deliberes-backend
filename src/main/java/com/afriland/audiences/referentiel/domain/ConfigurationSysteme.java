package com.afriland.audiences.referentiel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Paramètre de configuration globale modifiable par le DJ (ex : SEUIL_MONTANT_DEFAUT,
 * SEUIL_PROROGATIONS — cf. user stories §11.2, CONF01 à CONF07).
 * Cf. dictionnaire des données §2.4.3 (table {@code configuration_systeme}).
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

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
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
     * Auteur de la dernière modification (Q-112).
     *
     * <p>Une référence libre plutôt qu'un {@code @ManyToOne} : ce champ sert un rappel d'affichage,
     * pas une relation métier, et {@code historique_action} conserve de toute façon la trace
     * complète du changement. Nul tant que le paramètre n'a jamais été modifié, ou si le compte
     * de l'auteur a été supprimé depuis.
     */
    @Column(name = "dernier_auteur_id")
    private Long dernierAuteurId;
}
