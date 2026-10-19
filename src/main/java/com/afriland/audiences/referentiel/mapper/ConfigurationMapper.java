package com.afriland.audiences.referentiel.mapper;

import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.service.RegleConfiguration;
import com.afriland.audiences.referentiel.web.dto.ConfigurationResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ConfigurationMapper {

    /**
     * Hand-written: the input rule comes from {@link RegleConfiguration}, not from the entity, so
     * MapStruct has nothing to derive here.
     */
    default ConfigurationResponse toResponse(ConfigurationSysteme configuration) {
        RegleConfiguration regle = RegleConfiguration.pour(configuration.getCle()).orElse(null);
        return new ConfigurationResponse(
                configuration.getCle(),
                configuration.getValeur(),
                configuration.getDescription(),
                configuration.getModifiablePar(),
                configuration.getDateModification(),
                configuration.getDernierAuteurId(),
                regle == null ? null : regle.type(),
                regle == null ? null : regle.minimum(),
                regle == null ? null : regle.maximum(),
                regle == null ? List.of() : regle.valeursPossibles(),
                regle != null && regle.videAutorise());
    }
}
