package com.afriland.audiences.audience.repository;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AudienceRepository extends JpaRepository<Audience, Long> {

    /**
     * RG-AUD-06 : doublon d'audience. Appelé avec {@code ANNULEE} (Q-76) — une audience annulée ne
     * compte pas : replanifier à la même date après un renvoi n'est pas un doublon.
     */
    boolean existsByEtapeIdAndDatePlanifieeAndStatutNot(Long etapeId, LocalDate datePlanifiee, StatutAudience statut);

    /** FR-AUD-05 : calendrier et exports. Appelé avec {@code ANNULEE} (Q-76) : ils l'écartent. */
    List<Audience> findByDatePlanifieeBetweenAndStatutNot(LocalDate debut, LocalDate fin, StatutAudience statut);

    /** RG-AUD-03 (rappels J-7/J-3/J-1) : audiences encore planifiées à une date donnée. */
    List<Audience> findByStatutAndDatePlanifiee(StatutAudience statut, LocalDate datePlanifiee);

    /**
     * Les audiences d'un jour donné, annulées exclues : le journal des audiences (écran 44).
     *
     * <p>Paginé comme les autres listes du système : une juridiction chargée aligne les audiences,
     * et rien ne garantit qu'une journée en compte dix plutôt que cent.
     */
    Page<Audience> findByDatePlanifieeAndStatutNot(LocalDate datePlanifiee, StatutAudience statut, Pageable pageable);

    /**
     * Les audiences d'une fenêtre restées au statut demandé.
     *
     * <p>Sert à lister les comptes rendus attendus : une audience dont la date est passée et qui est
     * encore `PLANIFIEE` est, <b>par construction</b>, une audience dont le compte rendu n'a pas été
     * saisi — c'est la saisie qui la fait passer à `TENUE`. Aucune colonne supplémentaire n'est
     * nécessaire pour connaître les manquements.
     */
    Page<Audience> findByStatutAndDatePlanifieeBetween(
            StatutAudience statut, LocalDate debut, LocalDate fin, Pageable pageable);

    /** FR-REP-02 (tableau de bord, M13) : audiences encore à venir, tous dossiers confondus. */
    long countByStatut(StatutAudience statut);

    /** FR-REP-01 (rapport périodique, M13) : audiences tenues dans la période. */
    long countByStatutAndDateTenueBetween(StatutAudience statut, LocalDate debut, LocalDate fin);

    /** M15 : onglet « Audiences » de la fiche dossier (Q-68), plus récente d'abord. */
    Page<Audience> findByDossierIdOrderByDatePlanifieeDesc(Long dossierId, Pageable pageable);
}
