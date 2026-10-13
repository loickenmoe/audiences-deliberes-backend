package com.afriland.audiences.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface HistoriqueActionRepository extends JpaRepository<HistoriqueAction, Long>,
        JpaSpecificationExecutor<HistoriqueAction> {

    Page<HistoriqueAction> findByDossierIdOrderByDateActionDesc(Long dossierId, Pageable pageable);

    /**
     * Natures d'action présentes dans tout l'historique (Q-112), pour le filtre de l'écran d'audit.
     *
     * <p>Volontairement <b>indépendante des filtres en cours</b> : une liste de choix qui se réduit
     * à mesure qu'on filtre empêche de changer d'avis sans tout vider. Elle vient de la donnée et
     * non de l'énumération complète, pour ne jamais proposer une nature qui ne renverrait rien.
     */
    @Query("select distinct h.code from HistoriqueAction h order by h.code")
    List<TypeActionHistorique> actionsPresentes();

    /** FR-GED-04 (rapport journalier) : nombre de dossiers distincts manipulés à une date donnée. */
    @Query("select count(distinct h.dossierId) from HistoriqueAction h "
            + "where h.dossierId is not null and h.dateAction >= :debut and h.dateAction < :fin")
    long countDistinctDossierIdByDateActionBetween(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Observation 14 : l'activité de chacun sur une période, comptée par nature d'action.
     *
     * <p><b>Une ligne par personne et par code</b>, et non une colonne par indicateur : la liste des
     * natures suivies changera, et une requête qui les nomme une à une devrait être réécrite à
     * chaque fois. Le service assemble.
     *
     * <p><b>Ce que cette requête ne mesure pas, et ne peut pas mesurer.</b> Elle compte des
     * <b>actions</b>, pas du travail : l'application n'horodate pas des durées, et rien ici ne dit
     * si un dossier était simple ou difficile. C'est la limite posée avec le métier (N11) - on
     * compte, on n'apprécie pas.
     */
    @Query("select h.utilisateur.id as utilisateurId, h.code as code, count(h) as total "
            + "from HistoriqueAction h "
            + "where h.code in :codes and h.dateAction >= :debut and h.dateAction < :fin "
            + "group by h.utilisateur.id, h.code")
    List<ActiviteParPersonne> compterActivitesParPersonne(@Param("codes") Collection<TypeActionHistorique> codes,
                                                          @Param("debut") LocalDateTime debut,
                                                          @Param("fin") LocalDateTime fin);

    /**
     * Le délai moyen, en jours, entre une audience et la saisie de son compte rendu.
     *
     * <p><b>Requête native, et il faut dire pourquoi.</b> Le lien entre l'action et l'audience passe
     * par le {@code jsonb} des détails : {@code details->>'audienceId'}. JPQL ne sait pas lire un
     * document JSON, et dupliquer l'identifiant dans une colonne pour les besoins d'un seul rapport
     * ferait payer à toute l'écriture d'audit le prix d'une lecture.
     *
     * <p>Les détails sans {@code audienceId} - il n'y en a pas pour ce code, mais une donnée
     * ancienne pourrait en manquer - sont ignorés plutôt que de fausser la moyenne.
     *
     * <p><b>{@code jsonb_exists} et non l'opérateur {@code ?}.</b> PostgreSQL écrit cette présence
     * {@code details ? 'audienceId'}, mais {@code ?} est le marqueur de paramètre positionnel de
     * JDBC : le pilote le remplacerait par une valeur, et la requête échouerait à l'exécution -
     * jamais à la compilation. La fonction fait la même chose sans cette ambiguïté.
     */
    @Query(value = """
            SELECT h.utilisateur_id AS utilisateurId,
                   AVG(h.date_action::date - a.date_planifiee) AS delaiMoyen
            FROM historique_action h
            JOIN audience a ON a.id = (h.details ->> 'audienceId')::bigint
            WHERE h.code = 'AUDIENCE_COMPTE_RENDU_ENREGISTRE'
              AND jsonb_exists(h.details, 'audienceId')
              AND h.date_action >= :debut AND h.date_action < :fin
            GROUP BY h.utilisateur_id
            """, nativeQuery = true)
    List<DelaiSaisieParPersonne> delaiMoyenDeSaisieParPersonne(@Param("debut") LocalDateTime debut,
                                                               @Param("fin") LocalDateTime fin);

    /**
     * L'activité d'<b>une</b> personne, comptée par nature d'action.
     *
     * <p><b>Pourquoi une requête distincte de {@link #compterActivitesParPersonne}.</b> Celle-là
     * sert le tableau comparatif de la direction, qui tient sur six natures choisies ; celle-ci
     * sert la fiche d'une personne, qui doit refléter <b>fidèlement</b> ce qu'elle a fait - décision
     * du métier du 2026-10-09. Les deux listes de codes n'ont donc pas la même taille, et confondre
     * les deux usages dans une seule requête obligerait l'appelant à filtrer ce qu'il vient de
     * demander.
     *
     * <p>Les natures <b>sans aucune action</b> sur la période ne ressortent pas : une fiche
     * d'activité n'est pas une liste de zéros, et quarante-huit lignes vides diraient moins que
     * cinq lignes pleines.
     */
    @Query("select h.code as code, count(h) as total "
            + "from HistoriqueAction h "
            + "where h.utilisateur.id = :utilisateurId and h.code in :codes "
            + "and h.dateAction >= :debut and h.dateAction < :fin "
            + "group by h.code")
    List<ActiviteParCode> compterActivitesDe(@Param("utilisateurId") Long utilisateurId,
                                             @Param("codes") Collection<TypeActionHistorique> codes,
                                             @Param("debut") LocalDateTime debut,
                                             @Param("fin") LocalDateTime fin);

    /**
     * Les actions elles-mêmes, derrière un décompte : de quoi ouvrir le dossier concerné.
     *
     * <p>C'est ce que rend possible la colonne {@code dossier_id} de l'historique - un décompte sans
     * ce lien ne mènerait nulle part, et il faudrait relire le {@code jsonb} des détails, qui ne
     * porte pas toujours la même clé selon la nature de l'action.
     *
     * <p>Ordre décroissant : la plus récente d'abord, c'est celle qu'on cherche en cliquant.
     */
    @Query("select h from HistoriqueAction h "
            + "where h.utilisateur.id = :utilisateurId and h.code = :code "
            + "and h.dateAction >= :debut and h.dateAction < :fin "
            + "order by h.dateAction desc")
    Page<HistoriqueAction> listerActionsDe(@Param("utilisateurId") Long utilisateurId,
                                           @Param("code") TypeActionHistorique code,
                                           @Param("debut") LocalDateTime debut,
                                           @Param("fin") LocalDateTime fin,
                                           Pageable pageable);

    /**
     * Le délai moyen de saisie d'<b>une</b> personne, en jours. {@code null} si elle n'a saisi aucun
     * compte rendu - jamais zéro, qui se lirait comme une saisie le jour même.
     *
     * <p>Mêmes contraintes que {@link #delaiMoyenDeSaisieParPersonne} : requête native pour lire le
     * {@code jsonb}, et {@code jsonb_exists} plutôt que l'opérateur {@code ?}, que JDBC prendrait
     * pour un paramètre positionnel.
     */
    @Query(value = """
            SELECT AVG(h.date_action::date - a.date_planifiee)
            FROM historique_action h
            JOIN audience a ON a.id = (h.details ->> 'audienceId')::bigint
            WHERE h.code = 'AUDIENCE_COMPTE_RENDU_ENREGISTRE'
              AND h.utilisateur_id = :utilisateurId
              AND jsonb_exists(h.details, 'audienceId')
              AND h.date_action >= :debut AND h.date_action < :fin
            """, nativeQuery = true)
    Double delaiMoyenDeSaisieDe(@Param("utilisateurId") Long utilisateurId,
                                @Param("debut") LocalDateTime debut,
                                @Param("fin") LocalDateTime fin);

    /**
     * L'activité des avocats, comptée par personne et par nature (Q-136).
     *
     * <p>Identique dans sa forme à {@link #compterActivitesParPersonne}, et c'est voulu : le
     * tableau des avocats se lit comme celui des internes, et confondre les deux dans une requête
     * unique obligerait l'appelant à trier des lignes qu'il n'a pas demandées.
     *
     * <p>Le {@code keycloakId} et l'{@code identifiant} du compte voyagent avec le décompte : c'est
     * par eux que l'inscription au répertoire se retrouve, et les lire ici évite une seconde
     * requête par avocat.
     */
    @Query("select h.utilisateur.id as utilisateurId, h.utilisateur.nom as nom, "
            + "h.utilisateur.prenom as prenom, h.utilisateur.identifiant as identifiant, "
            + "h.utilisateur.keycloakId as keycloakId, h.code as code, count(h) as total "
            + "from HistoriqueAction h "
            + "where h.code in :codes and h.dateAction >= :debut and h.dateAction < :fin "
            + "group by h.utilisateur.id, h.utilisateur.nom, h.utilisateur.prenom, "
            + "h.utilisateur.identifiant, h.utilisateur.keycloakId, h.code")
    List<ActiviteDeposee> compterDepotsParPersonne(@Param("codes") Collection<TypeActionHistorique> codes,
                                                   @Param("debut") LocalDateTime debut,
                                                   @Param("fin") LocalDateTime fin);

    /** Un compte, une nature de dépôt, un décompte - avec de quoi retrouver l'inscription. */
    interface ActiviteDeposee {
        Long getUtilisateurId();

        String getNom();

        String getPrenom();

        String getIdentifiant();

        String getKeycloakId();

        TypeActionHistorique getCode();

        long getTotal();
    }

    /** Une nature d'action et son décompte, pour la fiche d'une personne. */
    interface ActiviteParCode {
        TypeActionHistorique getCode();

        long getTotal();
    }

    /** Une personne, une nature d'action, un décompte. */
    interface ActiviteParPersonne {
        Long getUtilisateurId();

        TypeActionHistorique getCode();

        long getTotal();
    }

    /** Une personne et son délai moyen de saisie, en jours. Nul si elle n'a saisi aucun compte rendu. */
    interface DelaiSaisieParPersonne {
        Long getUtilisateurId();

        Double getDelaiMoyen();
    }
}
