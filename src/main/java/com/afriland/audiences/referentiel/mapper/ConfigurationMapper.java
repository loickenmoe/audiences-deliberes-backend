package com.afriland.audiences.referentiel.mapper;

import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.service.RegleConfiguration;
import com.afriland.audiences.referentiel.web.dto.ConfigurationResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ConfigurationMapper {

    /**
     * Écrit à la main : la règle de saisie (Q-91) ne vient pas de l'entité mais de
     * {@link RegleConfiguration}, MapStruct n'a donc rien à dériver ici.
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
                regle == null ? List.of() : regle.valeursPossibles());
    }
}
