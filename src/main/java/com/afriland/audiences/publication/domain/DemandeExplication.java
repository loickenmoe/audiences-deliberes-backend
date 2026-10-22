package com.afriland.audiences.publication.domain;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Request for explanation sent by management to a lawyer, and the lawyer's answer.
 *
 * <p>Follows the alert raised when two consecutive hearings have no report, so that the exchange is
 * kept in the application.
 *
 * <p><b>Attached to the lawyer, not to a publication</b>: it concerns an <b>absence</b>, so there is
 * no publication to attach it to. This is what distinguishes it from {@link CorrespondanceAvocat}.
 *
 * <p><b>The status is not stored.</b> A request is pending while {@code dateReponse} is null; an
 * extra column could contradict the other two.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "demande_explication")
public class DemandeExplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avocat_id", nullable = false)
    private Avocat avocat;

    /**
     * Case file of the last missed hearing: it places the request in context without restricting it.
     *
     * <p>Optional, because the request concerns a <b>conduct</b> (missed hearings across several case
     * files), not a single matter. It is used for the history, which is always recorded on a case file.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id")
    private Dossier dossier;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", nullable = false)
    private Utilisateur auteur;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @CreationTimestamp
    @Column(name = "date_demande", updatable = false)
    private LocalDateTime dateDemande;

    @Column(columnDefinition = "TEXT")
    private String reponse;

    @Column(name = "date_reponse")
    private LocalDateTime dateReponse;

    /** Pending until the lawyer has answered. Derived, never stored. */
    public boolean estEnAttente() {
        return dateReponse == null;
    }
}
