package com.afriland.audiences.alerte.repository;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlerteRepository extends JpaRepository<Alerte, Long> {

    Page<Alerte> findByDestinataireId(Long destinataireId, Pageable pageable);

    Page<Alerte> findByDestinataireIdAndStatut(Long destinataireId, StatutAlerte statut, Pageable pageable);

    /**
     * Anti-doublon pour le déclenchement planifié (ex. {@code CHARGE_MAX_AVOCAT}, §M5) : une
     * alerte encore {@code DECLENCHEE} portant le même marqueur (préfixe stable de
     * {@code declencheur}, ex. {@code "[avocatId:42] "}) signale que le sujet est déjà notifié —
     * inutile de la redéclencher tant qu'elle n'a pas été traitée.
     */
    boolean existsByTypeAndDestinataireIdAndStatutAndDeclencheurStartingWith(
            TypeAlerte type, Long destinataireId, StatutAlerte statut, String marqueur);
}
