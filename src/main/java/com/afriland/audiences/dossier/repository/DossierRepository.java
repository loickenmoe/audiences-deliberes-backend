package com.afriland.audiences.dossier.repository;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

import com.afriland.audiences.dossier.domain.Dossier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DossierRepository extends JpaRepository<Dossier, Long>, JpaSpecificationExecutor<Dossier> {

    Optional<Dossier> findByReference(String reference);

    boolean existsByReference(String reference);

    /** Reference only, for external notifications: no need to load the whole case. */
    @Query("SELECT d.reference FROM Dossier d WHERE d.id = :id")
    Optional<String> findReferenceById(@Param("id") Long id);

    List<Dossier> findByClientId(Long clientId);

    /** FR-GED-04 (daily report): cases opened on a given date. */
    long countByCreatedAtBetween(LocalDateTime debut, LocalDateTime fin);

    /**
     * FR-REP-01, restricted by category. A variant, not a replacement:
     * {@link #countByCreatedAtBetween} also serves the document management daily report, which has
     * no category.
     */
    @Query("SELECT COUNT(d) FROM Dossier d "
            + "WHERE d.createdAt BETWEEN :debut AND :fin AND d.categorie IN :categories")
    long compterCreesEntre(@Param("debut") LocalDateTime debut,
                           @Param("fin") LocalDateTime fin,
                           @Param("categories") List<CategorieDossier> categories);

    /**
     * Lawyer portal: only the cases the lawyer is assigned to. The restriction is in the query
     * itself, never a later filter, so the portfolio cannot leak to an external provider.
     * <p>
     * The order is written in the query rather than passed as a {@code Sort}: the root is
     * {@code DossierAvocat}, which has no {@code createdAt}.
     */
    @Query(value = "SELECT da.dossier FROM DossierAvocat da WHERE da.avocat.id = :avocatId "
            + "ORDER BY da.dossier.createdAt DESC, da.dossier.id DESC",
            countQuery = "SELECT COUNT(da) FROM DossierAvocat da WHERE da.avocat.id = :avocatId")
    Page<Dossier> findDossiersDeLAvocat(@Param("avocatId") Long avocatId, Pageable pageable);

    /**
     * The same cases, <b>searched by reference</b> on the server, so a lawyer with more cases than
     * one page can still select older ones.
     * <p>The lawyer restriction remains in the query, never added afterwards.
     */
    @Query(value = "SELECT da.dossier FROM DossierAvocat da WHERE da.avocat.id = :avocatId "
            + "AND LOWER(da.dossier.reference) LIKE LOWER(CONCAT('%', :reference, '%')) "
            + "ORDER BY da.dossier.createdAt DESC, da.dossier.id DESC",
            countQuery = "SELECT COUNT(da) FROM DossierAvocat da WHERE da.avocat.id = :avocatId "
                    + "AND LOWER(da.dossier.reference) LIKE LOWER(CONCAT('%', :reference, '%'))")
    Page<Dossier> rechercherDossiersDeLAvocat(@Param("avocatId") Long avocatId,
                                              @Param("reference") String reference, Pageable pageable);
}
