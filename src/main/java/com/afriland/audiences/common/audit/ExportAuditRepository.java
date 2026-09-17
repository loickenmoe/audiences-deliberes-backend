package com.afriland.audiences.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportAuditRepository extends JpaRepository<ExportAudit, Long> {

    /** Les archives, de la plus récente à la plus ancienne, `id` départageant les ex æquo (Q-92). */
    Page<ExportAudit> findAllByOrderByDateExportDescIdDesc(Pageable pageable);
}
