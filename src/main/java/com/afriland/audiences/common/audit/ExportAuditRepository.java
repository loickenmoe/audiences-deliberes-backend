package com.afriland.audiences.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExportAuditRepository extends JpaRepository<ExportAudit, Long> {

    /** Les archives, de la plus récente à la plus ancienne, `id` départageant les ex æquo (Q-92). */
    Page<ExportAudit> findAllByOrderByDateExportDescIdDesc(Pageable pageable);

    /**
     * Prochain numéro de référence (Q-114), lu <b>avant</b> de produire le fichier.
     *
     * <p>La référence doit figurer dans le PDF, donc exister avant lui — alors que l'identifiant de
     * la ligne n'existe qu'après l'insertion, laquelle exige l'empreinte, laquelle exige le fichier.
     * Une séquence indépendante rompt ce cercle.
     */
    @Query(value = "SELECT nextval('export_audit_reference_seq')", nativeQuery = true)
    long prochainNumeroReference();

    /** Vérification d'un fichier détenu par un tiers (Q-114) : recherche par égalité d'empreinte. */
    Optional<ExportAudit> findByEmpreinte(String empreinte);
}
