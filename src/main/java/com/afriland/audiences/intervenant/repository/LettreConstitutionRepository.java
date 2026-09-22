package com.afriland.audiences.intervenant.repository;

import com.afriland.audiences.intervenant.domain.LettreConstitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LettreConstitutionRepository extends JpaRepository<LettreConstitution, Long> {

    Optional<LettreConstitution> findByDemandeId(Long demandeId);

    boolean existsByNumero(Integer numero);

    /**
     * Le numéro suivant, proposé par la séquence.
     *
     * <p>Une séquence et non {@code max(numero) + 1} : deux validations simultanées liraient le même
     * maximum et se disputeraient le même numéro, que la contrainte d'unicité ferait alors échouer
     * au hasard de l'ordonnancement. La séquence sert un numéro distinct à chacune.
     *
     * <p>Elle <b>propose</b> : le numéro reste modifiable, et c'est l'unicité en base qui garantit
     * qu'on n'en serve pas deux fois le même.
     */
    @Query(value = "SELECT nextval('lettre_constitution_numero_seq')", nativeQuery = true)
    long prochainNumero();
}
