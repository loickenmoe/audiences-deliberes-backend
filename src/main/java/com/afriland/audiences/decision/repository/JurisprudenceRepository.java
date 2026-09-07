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
     * UC-DEF-03 : recherche par mots-clés/nature/juridiction, chaque filtre optionnel (ignoré si
     * {@code null}). Requête native : {@code mots_cles} est un tableau Postgres (`text[]`), non une
     * collection JPA — l'opérateur {@code ANY(...)} n'a pas d'équivalent JPQL portable ici.
     */
    @Query(value = "SELECT * FROM jurisprudence j WHERE "
            + "(:motCle IS NULL OR :motCle = ANY(j.mots_cles)) AND "
            + "(:natureDecision IS NULL OR j.nature_decision = :natureDecision) AND "
            + "(:juridiction IS NULL OR j.juridiction = :juridiction) "
            + "ORDER BY j.date_decision DESC",
            countQuery = "SELECT count(*) FROM jurisprudence j WHERE "
                    + "(:motCle IS NULL OR :motCle = ANY(j.mots_cles)) AND "
                    + "(:natureDecision IS NULL OR j.nature_decision = :natureDecision) AND "
                    + "(:juridiction IS NULL OR j.juridiction = :juridiction)",
            nativeQuery = true)
    Page<Jurisprudence> rechercher(@Param("motCle") String motCle, @Param("natureDecision") String natureDecision,
                                    @Param("juridiction") String juridiction, Pageable pageable);
}
