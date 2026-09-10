package com.afriland.audiences.audience.repository;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
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

    /** FR-REP-02 (tableau de bord, M13) : audiences encore à venir, tous dossiers confondus. */
    long countByStatut(StatutAudience statut);

    /** FR-REP-01 (rapport périodique, M13) : audiences tenues dans la période. */
    long countByStatutAndDateTenueBetween(StatutAudience statut, LocalDate debut, LocalDate fin);

    /** M15 : onglet « Audiences » de la fiche dossier (Q-68), plus récente d'abord. */
    List<Audience> findByDossierIdOrderByDatePlanifieeDesc(Long dossierId);
}
