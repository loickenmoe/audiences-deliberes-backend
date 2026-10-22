package com.afriland.audiences.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExportAuditRepository extends JpaRepository<ExportAudit, Long> {

    /** Archives newest first, `id` breaking ties. */
    Page<ExportAudit> findAllByOrderByDateExportDescIdDesc(Pageable pageable);

    /**
     * Next reference number, read before producing the file. The reference must be printed in the
     * file, while the row id only exists after insert (which needs the hash, hence the file); an
     * independent sequence breaks that cycle.
     */
    @Query(value = "SELECT nextval('export_audit_reference_seq')", nativeQuery = true)
    long prochainNumeroReference();

    /** Lookup by exact hash, to verify a file held by a third party. */
    Optional<ExportAudit> findByEmpreinte(String empreinte);
}
