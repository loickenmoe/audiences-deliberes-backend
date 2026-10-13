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

    /** Référence seule, pour les notifications externes : inutile de charger le dossier entier. */
    @Query("SELECT d.reference FROM Dossier d WHERE d.id = :id")
    Optional<String> findReferenceById(@Param("id") Long id);

    List<Dossier> findByClientId(Long clientId);

    /** FR-GED-04 (rapport journalier) : dossiers ouverts (affaires nouvelles) à une date donnée. */
    long countByCreatedAtBetween(LocalDateTime debut, LocalDateTime fin);

    /**
     * FR-REP-01, recadré par catégorie (Q-138).
     *
     * <p><b>Une variante et non un remplacement</b> : {@link #countByCreatedAtBetween} sert aussi au
     * rapport journalier de la GED, qui n'a pas de notion de catégorie. Les deux coexistent donc,
     * et celle-ci ne sert qu'au rapport d'activité.
     */
    @Query("SELECT COUNT(d) FROM Dossier d "
            + "WHERE d.createdAt BETWEEN :debut AND :fin AND d.categorie IN :categories")
    long compterCreesEntre(@Param("debut") LocalDateTime debut,
                           @Param("fin") LocalDateTime fin,
                           @Param("categories") List<CategorieDossier> categories);

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

    /**
     * Les mêmes dossiers, <b>cherchés par référence</b>.
     *
     * <p><b>Le défaut qu'elle corrige.</b> Le sélecteur du portail chargeait une seule page et n'y
     * trouvait que les dossiers les plus récents : un avocat qui en suit plus que la page ne peut
     * plus désigner les autres — ni déposer de demande de frais, ni de compte rendu, pour ses
     * dossiers anciens. Le dossier existait, l'affectation aussi : seule la liste était tronquée,
     * en silence. C'est le même piège que Q-105 sur l'annuaire des avocats, et la même réponse :
     * chercher sur le serveur plutôt que filtrer une page déjà réduite.
     *
     * <p>La restriction à l'avocat reste portée par la requête, jamais ajoutée après coup.
     */
    @Query(value = "SELECT da.dossier FROM DossierAvocat da WHERE da.avocat.id = :avocatId "
            + "AND LOWER(da.dossier.reference) LIKE LOWER(CONCAT('%', :reference, '%')) "
            + "ORDER BY da.dossier.createdAt DESC, da.dossier.id DESC",
            countQuery = "SELECT COUNT(da) FROM DossierAvocat da WHERE da.avocat.id = :avocatId "
                    + "AND LOWER(da.dossier.reference) LIKE LOWER(CONCAT('%', :reference, '%'))")
    Page<Dossier> rechercherDossiersDeLAvocat(@Param("avocatId") Long avocatId,
                                              @Param("reference") String reference, Pageable pageable);
}
