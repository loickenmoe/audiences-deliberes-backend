package com.afriland.audiences.referentiel.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Utilisateur interne ou externe du système, authentifié via Keycloak. Provisionné à la première
 * requête authentifiée à partir des claims du jeton (cf. module {@code common}, prévu en M3) —
 * aucune gestion de mot de passe n'est implémentée ici (REF-05 §1.3).
 * Cf. dictionnaire des données §2.4.1 (table {@code utilisateur}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "utilisateur")
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "keycloak_id", nullable = false, unique = true)
    private String keycloakId;

    /**
     * Q-84 : identifiant de connexion ({@code preferred_username}) — celui que le DJ saisit comme
     * compte d'un avocat. Nul pour un utilisateur provisionné avant Q-84 jusqu'à sa requête suivante.
     */
    @Size(max = 255)
    @Column(unique = true)
    private String identifiant;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String nom;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String prenom;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false)
    private String email;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profil_id", nullable = false)
    private Profil profil;

    @Builder.Default
    private Boolean actif = true;

    /**
     * Image de la signature manuscrite, déposée par son <b>seul titulaire</b> (métier, 2026-10-06).
     *
     * <p><b>Confort de présentation, non valeur probante.</b> La lettre de constitution part avec
     * l'image au lieu d'un nom à signer à la main. Ce qui fait preuve reste la trace horodatée de
     * qui a signé - {@code demande_constitution.valide_par} et l'historique - et non cette image :
     * l'effacer ne retirerait rien à la preuve, la falsifier n'en créerait aucune.
     *
     * <p>{@code LAZY} : quelques dizaines de kilo-octets qu'aucune liste d'utilisateurs n'a besoin
     * de charger. Sans cela, l'annuaire les transérerait tous à chaque appel.
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "signature")
    private byte[] signature;

    /** {@code image/png} ou {@code image/jpeg} : les seuls types que le PDF sait apposer. */
    @Size(max = 50)
    @Column(name = "signature_type", length = 50)
    private String signatureType;

    @Column(name = "signature_deposee_le")
    private LocalDateTime signatureDeposeeLe;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
