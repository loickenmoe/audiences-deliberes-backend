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
 * Internal or external system user, authenticated via Keycloak. Provisioned on the first
 * authenticated request from the token claims (see {@code common} module); no password management
 * is implemented here (REF-05 §1.3).
 * See data dictionary §2.4.1 (table {@code utilisateur}).
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
     * Login identifier ({@code preferred_username}), the one the DJ enters as a lawyer's account.
     * May be null for a previously provisioned user until their next request.
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
     * Image of the handwritten signature, uploaded by its <b>holder only</b>.
     *
     * <p><b>A presentation convenience, not evidence.</b> The appointment letter carries the image
     * instead of a name to sign by hand. Evidence remains the timestamped record of who signed
     * ({@code demande_constitution.valide_par} and the history), not this image.
     *
     * <p>{@code LAZY}: a few dozen kilobytes that no user list needs to load.
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "signature")
    private byte[] signature;

    /** {@code image/png} or {@code image/jpeg}: the only types the PDF can embed. */
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
