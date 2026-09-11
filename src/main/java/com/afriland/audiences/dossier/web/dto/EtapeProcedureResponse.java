package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;

public record EtapeProcedureResponse(Long id, Long dossierId, TypeEtape type, StatutCycleVie statutCycleVie) {
}
