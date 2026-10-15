package com.afriland.audiences.delibere.repository;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

import com.afriland.audiences.delibere.domain.ResultatDelibere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DelibereRepository extends JpaRepository<Delibere, Long> {

    /** Délibérés dont le délai de recours est échu et dont l'étape est toujours DELIBERE_VIDE (aucun recours exercé) — DelaiRecoursScheduler. */
    List<Delibere> findByDateEcheanceRecoursNotNullAndDateEcheanceRecoursLessThanEqualAndEtapeStatutCycleVie(
            LocalDateTime maintenant, StatutCycleVie statutCycleVie);

    /**
     * Indicateur de performance par avocat (RG-INT-01, `GET /repertoire/avocats`, M9) : nombre de
     * délibérés {@code FAVORABLE} rapporté au nombre total de délibérés rendus sur les dossiers où
     * l'avocat est affecté — seule mesure de « succès » disponible avant le module Décisions
     * définitives (M12, Q-52).
     */
    @Query("SELECT da.avocat.id AS avocatId, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "COUNT(d) AS total "
            + "FROM Delibere d JOIN com.afriland.audiences.dossier.domain.DossierAvocat da ON da.dossier = d.dossier "
            // Q-78 : un délibéré en attente ou rabattu n'est ni un succès ni un échec.
            + "WHERE d.resultat IS NOT NULL "
            + "GROUP BY da.avocat.id")
    List<PerformanceAvocat> calculerPerformanceParAvocat();

    interface PerformanceAvocat {
        Long getAvocatId();

        Long getFavorables();

        Long getTotal();
    }

    /**
     * FR-REP-02 (tableau de bord, M13) : la performance en recouvrement, toutes périodes confondues.
     *
     * <p><b>Les trois résultats sont comptés séparément</b> (métier, 2026-10-03, observation 12). Le
     * décompte ne portait que les favorables et le total ; « perdus » n'est pas
     * « total moins gagnés », car un délibéré {@code MIXTE} n'est ni l'un ni l'autre - le déduire
     * aurait compté les affaires partiellement gagnées comme des échecs.
     */
    @Query("SELECT SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.DEFAVORABLE THEN 1L ELSE 0L END) AS defavorables, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.MIXTE THEN 1L ELSE 0L END) AS mixtes, "
            + "COUNT(d) AS total FROM Delibere d "
            + "WHERE d.dossier.categorie IN :categories "
            + "AND d.resultat IS NOT NULL")
    PerformanceGlobale calculerResultats(@Param("categories") List<CategorieDossier> categories);

    /**
     * Les délibérés que {@link #calculerTauxSuccesRecouvrement} compte, pour un résultat donné
     * (Q-137).
     *
     * <p><b>Les deux restrictions du compteur sont reprises telles quelles</b> : catégorie
     * {@code RECOUVREMENT} et résultat renseigné. La première surprendra peut-être - l'écran dit
     * « Litiges gagnés » sans la mentionner -, mais le détail doit rendre ce que le compteur compte,
     * pas ce que son libellé laisse croire. L'écart entre les deux est signalé pour arbitrage,
     * il ne se corrige pas ici en silence.
     */
    @Query("SELECT d FROM Delibere d "
            + "WHERE d.dossier.categorie IN :categories "
            + "AND d.resultat = :resultat "
            + "ORDER BY d.dateDeliberee DESC, d.id DESC")
    Page<Delibere> listerParResultat(@Param("resultat") ResultatDelibere resultat,
                                     @Param("categories") List<CategorieDossier> categories,
                                     Pageable pageable);

    /** FR-REP-01 (rapport périodique, M13) : mêmes indicateurs, restreints aux délibérés rendus dans la période. */
    @Query("SELECT SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.FAVORABLE THEN 1L ELSE 0L END) AS favorables, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.DEFAVORABLE THEN 1L ELSE 0L END) AS defavorables, "
            + "SUM(CASE WHEN d.resultat = com.afriland.audiences.delibere.domain.ResultatDelibere.MIXTE THEN 1L ELSE 0L END) AS mixtes, "
            + "COUNT(d) AS total FROM Delibere d "
            + "WHERE d.dossier.categorie IN :categories "
            + "AND d.resultat IS NOT NULL AND d.dateDeliberee BETWEEN :debut AND :fin")
    PerformanceGlobale calculerResultatsPeriode(@Param("debut") LocalDate debut,
                                                @Param("fin") LocalDate fin,
                                                @Param("categories") List<CategorieDossier> categories);

    interface PerformanceGlobale {
        Long getFavorables();

        /** Les délibérés défavorables : les litiges <b>perdus</b>, et eux seuls. */
        Long getDefavorables();

        /** Ni gagnés ni perdus. Les compter à part est la seule façon de ne pas les attribuer. */
        Long getMixtes();

        Long getTotal();
    }

    /** FR-REP-01 (rapport périodique, M13) : délibérés rendus dans la période — hors mises en délibéré en attente (Q-78). */
    long countByDateDelibereeBetweenAndResultatIsNotNull(LocalDate debut, LocalDate fin);

    /**
     * FR-REP-01, recadré par catégorie (Q-138). Une mise en délibéré sans résultat n'est pas un
     * délibéré rendu (Q-78) : la condition sur le résultat reste.
     */
    @Query("SELECT COUNT(d) FROM Delibere d WHERE d.resultat IS NOT NULL "
            + "AND d.dateDeliberee BETWEEN :debut AND :fin AND d.dossier.categorie IN :categories")
    long compterRendusEntre(@Param("debut") LocalDate debut,
                            @Param("fin") LocalDate fin,
                            @Param("categories") List<CategorieDossier> categories);

    /** M15 : onglet « Délibérés » de la fiche dossier (Q-68), plus récent d'abord. */
    List<Delibere> findByDossierIdOrderByDateDelibereeDesc(Long dossierId);

    /** Q-78 : au plus un délibéré en attente (ni vidé, ni rabattu) par étape. */
    boolean existsByEtapeIdAndResultatIsNullAndDateRabattementIsNull(Long etapeId);

    /**
     * Le dernier délibéré <b>rendu</b> sur une étape (2026-09-29).
     *
     * <p>Une étape peut en porter plusieurs — prorogée, rabattue, remise en délibéré. C est le
     * dernier rendu qui décide de ce qui peut suivre : recours ou clôture. Tri par identifiant
     * décroissant, soit l ordre de création — et non par date délibérée, qu une prorogation déplace.
     */
    Optional<Delibere> findFirstByEtapeIdAndResultatIsNotNullOrderByIdDesc(Long etapeId);
}
