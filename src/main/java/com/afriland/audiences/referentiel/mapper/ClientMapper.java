package com.afriland.audiences.referentiel.mapper;

import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.web.dto.ClientResponse;
import com.afriland.audiences.referentiel.web.dto.CreerClientRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    ClientResponse toResponse(Client client);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Client toEntity(CreerClientRequest request);
}
