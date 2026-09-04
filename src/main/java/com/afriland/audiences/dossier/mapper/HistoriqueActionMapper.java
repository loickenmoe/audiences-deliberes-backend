package com.afriland.audiences.dossier.mapper;

import com.afriland.audiences.common.audit.HistoriqueAction;
import com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HistoriqueActionMapper {

    @Mapping(source = "utilisateur.id", target = "utilisateurId")
    HistoriqueActionResponse toResponse(HistoriqueAction historiqueAction);
}
