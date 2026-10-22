package com.afriland.audiences.decision.repository;

import com.afriland.audiences.decision.domain.Jurisprudence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JurisprudenceRepository extends JpaRepository<Jurisprudence, Long> {

    /**
     * UC-DEF-03: search by keywords/nature/court, each filter optional (ignored when {@code null}).
     * Native query because {@code mots_cles} is a Postgres {@code text[]}, not a JPA collection.
     * Each text filter is a lowercase {@code LIKE} pattern prepared by the service (partial,
     * case-insensitive match). The {@code CAST}s type the null parameters for PostgreSQL.
     */
    @Query(value = "SELECT * FROM jurisprudence j WHERE "
            + "(CAST(:motCle AS text) IS NULL OR EXISTS (SELECT 1 FROM unnest(j.mots_cles) AS m(valeur) "
            + "WHERE lower(m.valeur) LIKE CAST(:motCle AS text) ESCAPE '\\')) AND "
            + "(CAST(:natureDecision AS text) IS NULL OR lower(j.nature_decision) LIKE CAST(:natureDecision AS text) ESCAPE '\\') AND "
            + "(CAST(:juridiction AS text) IS NULL OR lower(j.juridiction) LIKE CAST(:juridiction AS text) ESCAPE '\\') AND "
            + "(CAST(:dossierId AS bigint) IS NULL OR j.dossier_id = CAST(:dossierId AS bigint)) "
            + "ORDER BY j.date_decision DESC, j.id DESC",
            countQuery = "SELECT count(*) FROM jurisprudence j WHERE "
                    + "(CAST(:motCle AS text) IS NULL OR EXISTS (SELECT 1 FROM unnest(j.mots_cles) AS m(valeur) "
                    + "WHERE lower(m.valeur) LIKE CAST(:motCle AS text) ESCAPE '\\')) AND "
                    + "(CAST(:natureDecision AS text) IS NULL OR lower(j.nature_decision) LIKE CAST(:natureDecision AS text) ESCAPE '\\') AND "
                    + "(CAST(:juridiction AS text) IS NULL OR lower(j.juridiction) LIKE CAST(:juridiction AS text) ESCAPE '\\') AND "
                    + "(CAST(:dossierId AS bigint) IS NULL OR j.dossier_id = CAST(:dossierId AS bigint))",
            nativeQuery = true)
    Page<Jurisprudence> rechercher(@Param("motCle") String motCle, @Param("natureDecision") String natureDecision,
                                    @Param("juridiction") String juridiction, @Param("dossierId") Long dossierId,
                                    Pageable pageable);
}
