package com.afriland.audiences.dossier.mapper;

import com.afriland.audiences.dossier.domain.AuditSeuilDossier;
import com.afriland.audiences.dossier.web.dto.AuditSeuilResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditSeuilMapper {

    @Mapping(source = "dossier.id", target = "dossierId")
    @Mapping(source = "utilisateur.id", target = "utilisateurId")
    @Mapping(source = "validePar.id", target = "validePar")
    AuditSeuilResponse toResponse(AuditSeuilDossier audit);
}
