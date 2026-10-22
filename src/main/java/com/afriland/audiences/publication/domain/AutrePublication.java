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
 * Other publication by a lawyer: exhibit, decision or submission (US 5.3, RG-INT-06).
 * The file is stored through the {@code ged} module's {@code MinioStorageService} without creating
 * a GED {@code document} row (the publication has its own lifecycle).
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
