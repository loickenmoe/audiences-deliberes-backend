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

/**
 * Une agence de la banque (métier, 2026-10-03, observation 2).
 *
 * <p>Les deux codes d'agence d'un dossier — celle d'origine et celle du contentieux — étaient du
 * texte libre, sans vérification. Sur les 341 dossiers de recette, un seul code était renseigné :
 * {@code 5466546546}, qui ne désigne aucune agence. Un référentiel ferme la saisie et rend la
 * recherche possible.
 *
 * <p><b>Le code n'est pas la clé primaire.</b> Une agence se renomme, se déplace, ferme ; un
 * identifiant technique stable survit à cela, et les dossiers qui la citent gardent leur sens.
 * Le code reste unique, car c'est lui que la banque emploie.
 *
 * <p><b>Désactivée, jamais supprimée.</b> Un dossier ouvert sous une agence fermée reste un
 * dossier valide : effacer l'agence le rendrait illisible. {@code actif} la retire des listes de
 * choix sans toucher au passé.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agence")
public class Agence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Le code employé par la banque, par exemple {@code 00002} pour Bonanjo. Unique. */
    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String nom;

    /**
      * Obligatoire (métier, 2026-10-05). Deux agences peuvent porter le même nom : c'est la ville,
      * puis l'adresse, qui les séparent. Sans elle, l'homonyme ne se distingue qu'au hasard.
      */
    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String ville;

    @Size(max = 255)
    @Column(length = 255)
    private String adresse;

    @Size(max = 50)
    @Column(length = 50)
    private String telephone;

    @Builder.Default
    @Column(nullable = false)
    private Boolean actif = true;
}
