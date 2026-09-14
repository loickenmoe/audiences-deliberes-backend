package com.afriland.audiences.dossier.repository;

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

    List<Dossier> findByClientId(Long clientId);

    /** FR-GED-04 (rapport journalier) : dossiers ouverts (affaires nouvelles) à une date donnée. */
    long countByCreatedAtBetween(LocalDateTime debut, LocalDateTime fin);

    /**
     * Portail avocat (Q-96) : les dossiers auxquels un avocat est affecté, et eux seuls. La
     * restriction est portée par la requête elle-même, pas par un filtre appliqué après coup — un
     * filtre oublié laisserait fuir tout le portefeuille à un prestataire externe.
     * <p>
     * L'ordre est écrit dans la requête et non passé en {@code Sort} : la racine est
     * {@code DossierAvocat}, un tri sur {@code createdAt} s'y appliquerait et échouerait — c'est le
     * dossier sélectionné qui porte ce champ.
     */
    @Query(value = "SELECT da.dossier FROM DossierAvocat da WHERE da.avocat.id = :avocatId "
            + "ORDER BY da.dossier.createdAt DESC, da.dossier.id DESC",
            countQuery = "SELECT COUNT(da) FROM DossierAvocat da WHERE da.avocat.id = :avocatId")
    Page<Dossier> findDossiersDeLAvocat(@Param("avocatId") Long avocatId, Pageable pageable);
}
