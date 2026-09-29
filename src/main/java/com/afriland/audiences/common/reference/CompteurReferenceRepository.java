package com.afriland.audiences.common.reference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompteurReferenceRepository extends JpaRepository<CompteurReference, CompteurReference.Cle> {

    /**
     * Sert le numéro suivant pour cette famille et cette année.
     *
     * <p>Tout le travail est fait par la fonction {@code prochain_numero_reference} (V36) : créer le
     * compteur s'il n'existe pas, l'incrémenter sinon, et rendre le numéro — le tout en une seule
     * opération atomique. Le faire en Java supposerait de lire puis d'écrire, et deux créations
     * simultanées liraient la même valeur.
     *
     * <p>Même forme que {@code ExportAuditRepository.prochainNumeroReference} (V21) : un
     * {@code SELECT}, jamais une requête de modification, pour que rien ne dépende de la manière
     * dont Hibernate reconnaît le DML natif.
     */
    @Query(value = "SELECT prochain_numero_reference(:famille, :annee)", nativeQuery = true)
    int prochainNumero(@Param("famille") String famille, @Param("annee") int annee);
}
