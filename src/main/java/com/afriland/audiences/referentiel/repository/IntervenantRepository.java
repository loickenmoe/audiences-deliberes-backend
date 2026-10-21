package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Intervenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Polymorphic resolution of a provider (lawyer or other) for {@code demande_constitution.prestataire_id} (RG-INT-08). */
@Repository
public interface IntervenantRepository extends JpaRepository<Intervenant, Long> {

    /**
     * Lawyers only, or all other providers.
     *
     * <p>The filter applies to the <b>entity type</b>, materialised by the {@code JOINED} hierarchy
     * discriminator {@code intervenant.type}, so it runs in SQL along with pagination.
     *
     * <p>{@code nom} arrives <b>already lower-cased and wrapped in `%`</b>: see
     * {@code DemandeFraisRepository.rechercher} for the reason ({@code LOWER()} on a null parameter
     * fails on PostgreSQL).
     */
    @Query("SELECT i FROM Intervenant i WHERE (:avocatsSeuls IS NULL "
            + "OR (:avocatsSeuls = TRUE AND TYPE(i) = Avocat) "
            + "OR (:avocatsSeuls = FALSE AND TYPE(i) <> Avocat)) "
            + "AND (:nom IS NULL OR LOWER(i.nom) LIKE :nom)")
    Page<Intervenant> rechercher(@Param("avocatsSeuls") Boolean avocatsSeuls,
                                 @Param("nom") String nom,
                                 Pageable pageable);
}
