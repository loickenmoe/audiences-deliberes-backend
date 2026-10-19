package com.afriland.audiences.intervenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Content of an appointment letter, persisted with its number and freeze time.
 *
 * <p>{@link #dateGeneration} marks when the PDF was produced. After that the letter is never
 * rewritten: a correction produces a new letter with a new number, and the first stays in the
 * document store. A document that can be silently edited proves nothing.
 */
@Entity
@Table(name = "lettre_constitution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LettreConstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demande_constitution_id", nullable = false, unique = true)
    private DemandeConstitution demande;

    /**
     * Counter following "N°"; it does not reset each year. Null while the letter is a draft, so
     * abandoned requests leave no gaps in the official series. Drawn from the sequence at signature
     * unless the drafter imposed one (protected by the unique constraint).
     */
    @Column(unique = true)
    private Integer numero;

    /**
     * Rest of the number line after the counter, e.g. {@code First Bank/DAJR/DR/CM/ABN/18}. Stored
     * as one string and split on "/" by the UI, since the number of segments varies by department.
     */
    @Column(name = "suffixe_numero", nullable = false, length = 160)
    private String suffixeNumero;

    @Column(nullable = false, length = 80)
    private String lieu;

    @Column(name = "destinataire_nom", length = 160)
    private String destinataireNom;

    @Column(name = "destinataire_qualite", length = 120)
    private String destinataireQualite;

    @Column(name = "destinataire_ville", length = 80)
    private String destinataireVille;

    @Column(length = 255)
    private String concerne;

    @Column(nullable = false, length = 255)
    private String objet;

    /** Pre-filled from the template, with dotted lines wherever nothing is known. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String corps;

    @Column(nullable = false, length = 160)
    private String signataire;

    /** Set when the PDF is produced; while null, the letter is a draft. */
    @Column(name = "date_generation")
    private LocalDateTime dateGeneration;

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    /**
     * Author of the latest version. The line manager may correct the letter before signing, so the
     * drafter must be able to see on screen that it was rewritten, and by whom.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redige_par")
    private Utilisateur redigePar;

    /** True once the PDF is produced: the letter can no longer be modified. */
    public boolean estGelee() {
        return dateGeneration != null;
    }

    /**
     * Full printed line, e.g. {@code N° 4 /First Bank/DAJR/DR/CM/ABN/18}. For a draft (no number)
     * the slot stays blank, as on the paper template.
     */
    public String referenceComplete() {
        return "N° " + (numero == null ? "" : numero) + " /" + suffixeNumero;
    }

    @PrePersist
    void avantCreation() {
        dateCreation = LocalDateTime.now();
    }

    @PreUpdate
    void avantModification() {
        dateModification = LocalDateTime.now();
    }
}
