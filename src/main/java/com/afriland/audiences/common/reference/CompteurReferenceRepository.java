package com.afriland.audiences.common.reference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompteurReferenceRepository extends JpaRepository<CompteurReference, CompteurReference.Cle> {

    /**
     * Serves the next number for this family and year. The {@code prochain_numero_reference}
     * function (V36) creates or increments the counter atomically; a read-then-write in Java would
     * let two concurrent creations get the same value. Issued as a {@code SELECT}, not a modifying
     * query, so nothing depends on how Hibernate detects native DML.
     */
    @Query(value = "SELECT prochain_numero_reference(:famille, :annee)", nativeQuery = true)
    int prochainNumero(@Param("famille") String famille, @Param("annee") int annee);
}
