package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Intervenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Résolution polymorphe d'un prestataire (avocat ou autre) — {@code demande_constitution.prestataire_id} (RG-INT-08, M9). */
@Repository
public interface IntervenantRepository extends JpaRepository<Intervenant, Long> {

    /**
     * Les avocats seuls, ou tous les autres prestataires (Q-100).
     *
     * <p>Le filtre s'exprime sur le **type de l'entité**, que la hiérarchie {@code JOINED} matérialise
     * par le discriminant {@code intervenant.type} : il part donc en SQL, avec la pagination. Le
     * précédent code chargeait tout l'annuaire pour le filtrer en mémoire — sans conséquence à
     * l'échelle actuelle, mais c'est précisément ce que la pagination doit cesser de faire.
     *
     * <p>⚠ {@code nom} arrive **déjà en minuscules et encadré de `%`** : voir
     * {@code DemandeFraisRepository.rechercher} pour la raison — {@code LOWER()} sur un paramètre nul
     * échoue sur PostgreSQL.
     */
    @Query("SELECT i FROM Intervenant i WHERE (:avocatsSeuls IS NULL "
            + "OR (:avocatsSeuls = TRUE AND TYPE(i) = Avocat) "
            + "OR (:avocatsSeuls = FALSE AND TYPE(i) <> Avocat)) "
            + "AND (:nom IS NULL OR LOWER(i.nom) LIKE :nom)")
    Page<Intervenant> rechercher(@Param("avocatsSeuls") Boolean avocatsSeuls,
                                 @Param("nom") String nom,
                                 Pageable pageable);
}
