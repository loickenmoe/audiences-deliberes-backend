package com.afriland.audiences.alerte.repository;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.StatutDiffusion;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

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

    /**
     * Les alertes que {@link #countByTypeAndStatut} compte (Q-137), la plus récente d'abord.
     *
     * <p><b>Une ligne par alerte, et non par dossier.</b> C'est ce que le compteur compte : une
     * prorogation prévenant plusieurs destinataires produit plusieurs alertes. Regrouper
     * donnerait une liste plus courte que le nombre affiché.
     */
    Page<Alerte> findByTypeAndStatutOrderByIdDesc(TypeAlerte type, StatutAlerte statut, Pageable pageable);

    /**
     * Supervision du canal applicatif (Q-109, NF-ALR-02) : nombre d'alertes d'une issue donnée sur
     * une fenêtre. Un décompte par issue plutôt qu'un {@code GROUP BY} : trois requêtes servies par
     * le même index partiel, contre un agrégat dont il faudrait ensuite combler les issues absentes
     * — un zéro manquant se lit trop facilement comme un zéro mesuré.
     *
     * <p>La borne haute est <b>exclusive</b> : l'appelant passe le lendemain de la date de fin, ce
     * qui inclut toute la dernière journée sans dépendre de la précision des horodatages.
     */
    @Query("SELECT COUNT(a) FROM Alerte a WHERE a.statutDiffusion = :statut "
            + "AND a.dateDeclenchement >= :debut AND a.dateDeclenchement < :fin")
    long compterParDiffusion(@Param("statut") StatutDiffusion statut,
                                @Param("debut") LocalDateTime debut,
                                @Param("fin") LocalDateTime fin);

    /**
     * Alertes de la fenêtre que personne n'a ouvertes. Ce décompte ne participe à aucun taux : une
     * alerte techniquement délivrée mais jamais lue relève d'un autre problème que la délivrance,
     * et les mélanger brouillerait les deux.
     */
    @Query("SELECT COUNT(a) FROM Alerte a WHERE a.statut = com.afriland.audiences.alerte.domain.StatutAlerte.DECLENCHEE "
            + "AND a.dateDeclenchement >= :debut AND a.dateDeclenchement < :fin")
    long compterNonTraitees(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);
}
