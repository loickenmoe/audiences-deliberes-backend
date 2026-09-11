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

    /**
     * Anti-doublon pour un événement qui ne doit être signalé **qu'une seule fois dans son
     * existence** (ex. rappel J-7 d'une audience, échéance d'une alarme) — contrairement à
     * {@link #existsByTypeAndDestinataireIdAndStatutAndDeclencheurStartingWith}, le statut
     * {@code TRAITEE} ne doit pas autoriser un nouveau déclenchement du même événement.
     */
    boolean existsByTypeAndDestinataireIdAndDeclencheurStartingWith(
            TypeAlerte type, Long destinataireId, String marqueur);

    /** FR-REP-02 (tableau de bord, M13) : alertes de prorogation encore non traitées, tous destinataires confondus. */
    long countByTypeAndStatut(TypeAlerte type, StatutAlerte statut);
}
