package com.afriland.audiences.reporting.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * L'activité des avocats sur une période (Q-136), pendant du rapport nominatif interne (Q-132).
 *
 * <p><b>Pourquoi un second tableau et non quatre colonnes de plus.</b> Le porteur du projet l'a
 * relevé le 2026-10-13 : « Activité par personne » ne montrait que les profils internes. C'est
 * exact, et ce n'était pas réparable en y ajoutant les avocats : ce tableau compte six natures de
 * <b>production interne</b> - ouvrir un dossier, planifier une audience, enregistrer un compte
 * rendu, un délibéré, rédiger une lettre, contrôler des frais - dont un avocat n'accomplit
 * <b>aucune</b>. Il <b>dépose</b> ; c'est le juriste qui enregistre. L'y faire figurer n'aurait
 * affiché qu'une ligne de zéros, soit exactement ce que la conception refuse.
 *
 * <p><b>Deux populations, deux jeux d'indicateurs, une même règle.</b> Les avocats se comparent
 * entre eux sur ce qu'ils font réellement : déposer des comptes rendus, des demandes de frais,
 * d'autres publications, et répondre quand la direction demande des explications. Ni note, ni
 * classement par volume, ni total global - les lignes suivent l'ordre des <b>noms</b>, comme pour
 * les internes.
 *
 * <p><b>Les quatre natures comptées sont exclusives à l'avocat</b> : les trois dépôts et la réponse
 * à une demande d'explication sont tous {@code hasRole('AVOCAT')}. Grouper sur elles ne ramène donc
 * que des avocats, sans filtrer sur le profil - la même élégance que le tableau interne, et une
 * condition de moins à garder d'accord avec les habilitations.
 */
public record RapportAvocatsResponse(LocalDate dateDebut, LocalDate dateFin, List<ActiviteAvocat> lignes) {

    /** Vide quand aucun avocat n'a rien déposé sur la période : un 204, pas une page de zéros. */
    public boolean estVide() {
        return lignes.isEmpty();
    }

    /**
     * Un avocat et ses dépôts sur la période.
     *
     * @param avocatId {@code null} si son inscription au répertoire a été retirée depuis. Ses dépôts
     *                 restent comptés - les masquer ferait disparaître de l'activité réelle - mais
     *                 l'écran l'ouvre alors par son compte, faute d'inscription à désigner.
     * @param nom      le nom du répertoire quand il y est encore, celui du compte sinon.
     */
    public record ActiviteAvocat(
            Long utilisateurId,
            Long avocatId,
            String nom,
            long comptesRendusDeposes,
            long demandesFraisDeposees,
            long autresPublicationsDeposees,
            long explicationsFournies) {
    }
}
