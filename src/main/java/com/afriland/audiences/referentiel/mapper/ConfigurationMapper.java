package com.afriland.audiences.referentiel.mapper;

import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.web.dto.ConfigurationResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConfigurationMapper {

    ConfigurationResponse toResponse(ConfigurationSysteme configuration);
}
