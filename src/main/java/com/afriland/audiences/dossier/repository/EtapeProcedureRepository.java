package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.dossier.domain.Dossier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EtapeProcedureRepository extends JpaRepository<EtapeProcedure, Long> {

    List<EtapeProcedure> findByDossierIdOrderByTypeAsc(Long dossierId);

    /**
     * Étapes de plusieurs dossiers en une requête (QF-41). Sans elle, construire la réponse d'une
     * liste de dossiers interrogeait la base une fois par dossier.
     */
    List<EtapeProcedure> findByDossierIdInOrderByDossierIdAscTypeAsc(Collection<Long> dossierIds);

    Optional<EtapeProcedure> findByDossierIdAndType(Long dossierId, TypeEtape type);

    boolean existsByDossierIdAndType(Long dossierId, TypeEtape type);

    /**
     * Reste-t-il une étape vivante sur ce dossier ? (2026-09-29)
     *
     * <p>Sert la garde jurisprudentielle : une décision n'est « définitive » que lorsqu'elle n'est
     * plus susceptible de recours, donc lorsque plus aucune étape du dossier n'est en cours.
     */
    boolean existsByDossierIdAndStatutCycleVieNotIn(Long dossierId, List<StatutCycleVie> statuts);

    /** FR-REP-02 (tableau de bord, M13) : dossiers portant au moins une étape hors {@code statutsFermes} — même définition qu'au Q-53. */
    @Query("SELECT COUNT(DISTINCT e.dossier.id) FROM EtapeProcedure e WHERE e.statutCycleVie NOT IN :statutsFermes")
    long countDistinctDossierIdByStatutCycleVieNotIn(@Param("statutsFermes") List<StatutCycleVie> statutsFermes);

    /**
     * Les <b>affaires vidées</b> : les dossiers dont <b>plus aucune étape n'est en cours</b>
     * (métier, 2026-10-06, observation 12).
     *
     * <p><b>Pourquoi cette lecture de la règle.</b> La Direction a dit « une affaire est vidée
     * lorsqu'elle est à l'étape clôturée ». Un dossier porte plusieurs étapes — Instance, Appel,
     * les deux pourvois — et compter les étapes clôturées compterait le même dossier jusqu'à quatre
     * fois. Pire : un dossier dont l'Instance est clôturée mais dont l'Appel est ouvert n'est pas
     * vidé, il continue. La règle se lit donc au niveau du dossier, et c'est <b>la même notion
     * qu'au Q-53</b> : une décision n'est définitive que lorsque plus aucune étape n'est en cours.
     *
     * <p><b>{@code statutsFermes} comprend l'archivage</b>, et non la seule clôture. Une étape
     * archivée est passée par la clôture : la retirer du décompte ferait <b>baisser</b> le nombre
     * d'affaires vidées à mesure qu'on archive, ce que personne ne lirait comme autre chose qu'une
     * anomalie.
     *
     * <p>Ce compteur est donc le <b>complément exact</b> de
     * {@link #countDistinctDossierIdByStatutCycleVieNotIn}, déjà affiché à côté : les deux
     * s'additionnent pour faire les dossiers qui portent au moins une étape.
     *
     * <p>Sous-requête {@code not exists} et non jointure : une jointure multiplierait les lignes
     * par étape et falsifierait le compte.
     */
    @Query("""
            SELECT COUNT(DISTINCT e.dossier.id) FROM EtapeProcedure e
             WHERE NOT EXISTS (SELECT 1 FROM EtapeProcedure autre
                                WHERE autre.dossier = e.dossier
                                  AND autre.statutCycleVie NOT IN :statutsFermes)
            """)
    long countDossiersSansEtapeEnCours(@Param("statutsFermes") List<StatutCycleVie> statutsFermes);

    /**
     * Les dossiers que {@link #countDistinctDossierIdByStatutCycleVieNotIn} compte (Q-137).
     *
     * <p><b>Même prédicat, et c'est tout l'enjeu.</b> Le détail d'un bloc du tableau de bord doit
     * rendre exactement ce que son compteur compte : la moindre divergence ferait douter des deux
     * chiffres. {@code DISTINCT} sur le dossier, comme le compte, un dossier portant plusieurs
     * étapes en cours ne devant paraître qu'une fois.
     */
    @Query("SELECT DISTINCT e.dossier FROM EtapeProcedure e WHERE e.statutCycleVie NOT IN :statutsFermes")
    Page<Dossier> listerDossiersAvecEtapeEnCours(@Param("statutsFermes") List<StatutCycleVie> statutsFermes,
                                                 Pageable pageable);

    /** Les dossiers que {@link #countDossiersSansEtapeEnCours} compte - même sous-requête (Q-137). */
    @Query("""
            SELECT DISTINCT e.dossier FROM EtapeProcedure e
             WHERE NOT EXISTS (SELECT 1 FROM EtapeProcedure autre
                                WHERE autre.dossier = e.dossier
                                  AND autre.statutCycleVie NOT IN :statutsFermes)
            """)
    Page<Dossier> listerDossiersSansEtapeEnCours(@Param("statutsFermes") List<StatutCycleVie> statutsFermes,
                                                 Pageable pageable);
}
