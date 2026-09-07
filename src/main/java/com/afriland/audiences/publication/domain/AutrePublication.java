package com.afriland.audiences.publication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Autre publication d'un avocat — pièce, décision ou conclusion (US 5.3, RG-INT-06).
 * {@code cheminStockage} : colonne absente du schéma initial (comme {@code document.chemin_stockage},
 * Q-07), ajoutée en M11 — le fichier est déposé via le {@code MinioStorageService} du module
 * {@code ged} sans créer de ligne {@code document} GED (cycle de vie propre à la publication).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "autre_publication")
@DiscriminatorValue("AUTRE")
@PrimaryKeyJoinColumn(name = "id")
public class AutrePublication extends Publication {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false, length = 50)
    private TypeAutrePublication typeDocument;

    @Column(name = "chemin_stockage")
    private String cheminStockage;
}
