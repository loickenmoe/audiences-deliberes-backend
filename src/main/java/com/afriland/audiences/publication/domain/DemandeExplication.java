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
 * Demande d'explication adressée par la direction à un avocat, et sa réponse (N7 temps 2,
 * observation 8).
 *
 * <p><b>Ce qu'elle complète.</b> Le temps 1 signale à la direction que deux audiences consécutives
 * sont restées sans compte rendu. Ensuite, rien : le DJ écrivait hors de l'application, et plus
 * personne ne savait qui s'était expliqué. La demande vit donc ici, avec sa réponse.
 *
 * <p><b>Pourquoi elle pend à l'avocat, et non à une publication.</b> Elle porte sur une
 * <b>absence</b> : il n'y a précisément rien de publié à quoi l'accrocher. C'est ce qui la distingue
 * de {@link CorrespondanceAvocat}, dont le modèle a pourtant servi de patron.
 *
 * <p><b>Le statut ne se stocke pas.</b> Une demande est en attente tant que {@code dateReponse} est
 * nulle. Une colonne de plus pourrait dire le contraire des deux autres, et c'est toujours elle
 * qu'on finirait par croire.
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
     * Le dossier de la dernière audience manquée : il situe la demande sans la restreindre.
     *
     * <p>Facultatif, parce que la demande porte sur une <b>conduite</b> - des audiences manquées à
     * travers plusieurs dossiers - et non sur une affaire. Il sert à l'historique, qui s'enregistre
     * toujours sur un dossier.
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

    /** En attente tant que l'avocat n'a pas répondu. Déduit, jamais stocké. */
    public boolean estEnAttente() {
        return dateReponse == null;
    }
}
