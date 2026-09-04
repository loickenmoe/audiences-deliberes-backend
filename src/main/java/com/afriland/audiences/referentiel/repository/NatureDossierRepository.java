package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.domain.NatureDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NatureDossierRepository extends JpaRepository<NatureDossier, Long> {

    List<NatureDossier> findByActifTrue();

    List<NatureDossier> findByCategorieAndActifTrue(CategorieDossier categorie);

    Optional<NatureDossier> findByLibelleIgnoreCase(String libelle);
}
