package com.afriland.audiences.alerte.repository;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlerteRepository extends JpaRepository<Alerte, Long> {

    Page<Alerte> findByDestinataireId(Long destinataireId, Pageable pageable);

    Page<Alerte> findByDestinataireIdAndStatut(Long destinataireId, StatutAlerte statut, Pageable pageable);

    /**
     * Mes notifications, avec ses filtres optionnels (Q-102).
     *
     * <p>Une requête paramétrée plutôt que des méthodes dérivées : quatre critères facultatifs en
     * auraient demandé huit, à maintenir une par une. Le destinataire, lui, n'est **pas** optionnel —
     * il vient du jeton, jamais d'un paramètre, sinon chacun lirait les notifications des autres.
     */
    @Query("SELECT a FROM Alerte a WHERE a.destinataire.id = :destinataireId "
            + "AND (:statut IS NULL OR a.statut = :statut) "
            + "AND (:type IS NULL OR a.type = :type) "
            + "AND (:dossierId IS NULL OR a.dossierId = :dossierId)")
    Page<Alerte> rechercher(@Param("destinataireId") Long destinataireId,
                            @Param("statut") StatutAlerte statut,
                            @Param("type") TypeAlerte type,
                            @Param("dossierId") Long dossierId,
                            Pageable pageable);

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
