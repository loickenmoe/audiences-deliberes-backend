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

    /** Assistant's queue of publications to validate, filterable by status. */
    Page<Publication> findByStatutValidation(StatutPublication statut, Pageable pageable);

    /** Publications of a case file (publications tab of the case file page). */
    Page<Publication> findByDossierId(Long dossierId, Pageable pageable);

    /** Lawyer portal: their own filings, whatever the status, including rejected ones. */
    Page<Publication> findByAvocatId(Long avocatId, Pageable pageable);

    /** "My space": the lawyer filters their filings by status (filed, validated, rejected). */
    Page<Publication> findByAvocatIdAndStatutValidation(Long avocatId, StatutPublication statut, Pageable pageable);

    Page<Publication> findByDossierIdAndStatutValidation(Long dossierId, StatutPublication statut, Pageable pageable);

    /**
     * RG-INT-06 (amended): what an authorised user may see.
     *
     * <p>Two regimes depending on the filing type. A <b>hearing report</b> is readable <b>as soon as it
     * is filed</b> by everyone with consultation rights. An <b>other publication</b> (exhibit, decision,
     * submission) stays hidden until validated, since its compliance has not been checked.
     *
     * <p>The filter is applied <b>in the database</b>: filtering an already loaded page would skew the
     * total and produce gaps. This is why restrictions are written with {@code exists}, never with
     * joins. Investigating profiles do not use this query.
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
