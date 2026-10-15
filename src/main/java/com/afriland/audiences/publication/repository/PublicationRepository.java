package com.afriland.audiences.publication.repository;

import com.afriland.audiences.publication.domain.Publication;
import com.afriland.audiences.publication.domain.StatutPublication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {

    /** M15 (Q-68) : file de travail « publications à valider » de l'Assistante, filtrable par statut. */
    Page<Publication> findByStatutValidation(StatutPublication statut, Pageable pageable);

    /** M15 (Q-68) : publications d'un dossier — onglet correspondant de la fiche dossier. */
    Page<Publication> findByDossierId(Long dossierId, Pageable pageable);

    /** Portail avocat (Q-96) : ses propres dépôts, quel qu'en soit le statut — y compris rejetés. */
    Page<Publication> findByAvocatId(Long avocatId, Pageable pageable);

    /** Q-104 — « Mon espace » : l'avocat filtre ses dépôts par statut (déposé, validé, rejeté). */
    Page<Publication> findByAvocatIdAndStatutValidation(Long avocatId, StatutPublication statut, Pageable pageable);

    Page<Publication> findByDossierIdAndStatutValidation(Long dossierId, StatutPublication statut, Pageable pageable);

    /**
     * RG-INT-06 amendée (métier du 2026-10-05) : ce qu'un habilité a le droit de voir.
     *
     * <p>Deux régimes, selon la nature du dépôt. Un <b>compte rendu d'audience</b> est lisible
     * <b>dès son dépôt</b>, par tous ceux qui portent la consultation : le récit de l'avocat sur ce
     * qui s'est dit à l'audience n'attend pas un contrôle. Une <b>autre publication</b> — pièce,
     * décision, conclusion — reste invisible tant qu'elle n'est pas validée, sa conformité n'ayant
     * pas été vérifiée.
     *
     * <p>Le filtre est posé en <b>base</b> et non après coup : filtrer une page déjà chargée
     * fausserait le total et donnerait des pages trouées. C'est la raison pour laquelle le reste du
     * projet écrit ses restrictions en {@code exists}, jamais en jointure.
     *
     * <p>Aucune identité n'entre ici : la règle ne dépend plus de qui regarde, mais de ce qui est
     * regardé. Les profils instructeurs, eux, ne passent pas par cette requête.
     */
    @Query("""
            select p from Publication p
             where (:dossierId is null or p.dossier.id = :dossierId)
               and (:statut is null or p.statutValidation = :statut)
               and (p.statutValidation = com.afriland.audiences.publication.domain.StatutPublication.VALIDE
                    or type(p) = CompteRenduAudience)
            """)
    Page<Publication> findVisiblesParLesHabilites(@Param("dossierId") Long dossierId,
                                                  @Param("statut") StatutPublication statut,
                                                  Pageable pageable);
}
