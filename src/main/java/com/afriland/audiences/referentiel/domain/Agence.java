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
 * A bank branch.
 *
 * <p>The two branch codes of a case file (origin and litigation) used to be unchecked free text; a
 * reference list constrains input and makes search possible.
 *
 * <p><b>The code is not the primary key.</b> A branch can be renamed, moved or closed; a stable
 * technical id survives that, and the case files citing it keep their meaning. The code remains
 * unique, since it is what the bank uses.
 *
 * <p><b>Deactivated, never deleted.</b> A case file opened under a closed branch remains valid:
 * deleting the branch would make it unreadable. {@code actif} removes it from selection lists
 * without touching the past.
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

    /** Code used by the bank, e.g. {@code 00002} for Bonanjo. Unique. */
    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String nom;

    /**
     * Mandatory. Two branches can share the same name: the city, then the address, tell them apart.
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
