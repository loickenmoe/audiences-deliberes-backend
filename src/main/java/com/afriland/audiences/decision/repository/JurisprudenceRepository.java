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
     * collection JPA.
     * <p>
     * Depuis Q-89, chaque filtre textuel est un <b>motif {@code LIKE} déjà en minuscules</b>, préparé
     * par le service ({@code %terme%}, jokers échappés) : la recherche est partielle et insensible à
     * la casse — un mot-clé est retenu dès qu'un des mots indexés le contient. Les {@code CAST}
     * donnent un type aux paramètres nuls, que PostgreSQL ne saurait pas déduire seul.
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
