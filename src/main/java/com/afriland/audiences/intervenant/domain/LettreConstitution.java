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
 * Le contenu d'une lettre de constitution (Q-115 B1).
 *
 * <p><b>Ce qui change de nature.</b> La lettre était un <em>calcul</em> : cinq données mises en forme
 * au moment de la validation, puis oubliées. Elle devient un <em>document</em>, avec son contenu, son
 * numéro et son instant de gel. C'est ce qui permet de répondre à « pourquoi cette lettre dit-elle
 * ceci ? », question à laquelle la GED seule ne répondait pas.
 *
 * <p><b>Le gel.</b> {@link #dateGeneration} marque le moment où le PDF a été produit. Passé ce point,
 * la lettre ne se réécrit plus : une correction donne une <b>nouvelle</b> lettre, avec un nouveau
 * numéro, et la première reste dans la GED. Une pièce qu'on peut retoucher en silence ne prouve rien
 * — c'est le raisonnement déjà retenu pour les archives d'audit (Q-113).
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
     * Le compteur qui suit « N° ». Il <b>ne repart pas à 1 chaque année</b> : il court.
     *
     * <p><b>Nul tant que la lettre est un brouillon</b> (B2). Le réserver dès la rédaction
     * reviendrait à en brûler un à chaque demande sans suite, et les trous s'accumuleraient
     * définitivement dans une série de courrier officiel. Il est tiré de la séquence à la signature
     * — sauf si le rédacteur en a imposé un, ce que l'unicité protège.
     */
    @Column(unique = true)
    private Integer numero;

    /**
     * Le reste de la ligne, après le compteur : {@code First Bank/DAJR/DR/CM/ABN/18}.
     *
     * <p>Stocké d'un bloc et découpé sur « / » par l'écran. Le nombre de segments varie d'un service
     * à l'autre — quatre sur le papier de la DRH, six sur celui du recouvrement — et il ne doit pas
     * exiger une migration à chaque convention nouvelle.
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

    /** Pré-composé depuis le gabarit, pointillés compris là où rien n'est certain. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String corps;

    @Column(nullable = false, length = 160)
    private String signataire;

    /** Renseignée à la production du PDF. Tant qu'elle est nulle, la lettre est un brouillon. */
    @Column(name = "date_generation")
    private LocalDateTime dateGeneration;

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    /**
     * Qui a écrit la dernière version (métier, 2026-10-06).
     *
     * <p><b>Pourquoi cette colonne existe.</b> Q-115 B2 réservait la plume au juriste, pour que la
     * seconde signature garde son sens : le SH consultait sans pouvoir récrire. Le métier a tranché
     * l'inverse - le SH corrige avant de signer. La séparation des plumes cède, et c'est la
     * traçabilité qui la remplace : le juriste doit voir que sa lettre a été réécrite, et par qui.
     *
     * <p>L'historique le consigne aussi, mais il faut aller le lire. Ici, l'écran le montre.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redige_par")
    private Utilisateur redigePar;

    /** Vrai une fois le PDF produit : la lettre ne se modifie plus. */
    public boolean estGelee() {
        return dateGeneration != null;
    }

    /**
     * La ligne complète, telle qu'elle s'imprime : {@code N° 4 /First Bank/DAJR/DR/CM/ABN/18}.
     *
     * <p>Sans numéro — un brouillon — la place reste vide, comme sur le gabarit papier où elle
     * attend d'être remplie. Écrire « N° null » serait pire que ne rien écrire.
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
