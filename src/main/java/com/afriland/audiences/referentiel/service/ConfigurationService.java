package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.mapper.ConfigurationMapper;
import com.afriland.audiences.referentiel.repository.ConfigurationSystemeRepository;
import com.afriland.audiences.referentiel.web.dto.ConfigurationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * System parameters editable by the DJ (CONF01-07, US §11.2): {@code GET /configurations},
 * {@code PUT /configurations/{cle}}.
 */
@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private final ConfigurationSystemeRepository configurationSystemeRepository;
    private final ConfigurationMapper configurationMapper;
    private final HistoriqueActionService historiqueActionService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<ConfigurationResponse> lister() {
        return configurationSystemeRepository.findAll().stream().map(configurationMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ConfigurationSysteme getOrThrow(String cle) {
        return chargerConfiguration(cle);
    }

    private ConfigurationSysteme chargerConfiguration(String cle) {
        return configurationSystemeRepository.findByCle(cle)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Clé de configuration introuvable : " + cle));
    }

    /**
     * Updates a parameter. The value is validated then normalised according to the key's rule:
     * otherwise a wrong input would only surface elsewhere, as a 500 error when another module
     * converts the value.
     */
    @Transactional
    public ConfigurationResponse modifier(String cle, String nouvelleValeur) {
        ConfigurationSysteme configuration = chargerConfiguration(cle);
        String valeurRetenue = RegleConfiguration.pour(cle)
                .map(regle -> regle.valider(nouvelleValeur))
                .orElseGet(() -> exigerNonVide(cle, nouvelleValeur));
        String ancienneValeur = configuration.getValeur();
        configuration.setValeur(valeurRetenue);
        // Lets the settings screen show who last changed the value, without switching screens.
        configuration.setDernierAuteurId(currentUserService.getCurrentUser().getId());
        configuration = configurationSystemeRepository.save(configuration);

        // dossierId=null: action outside any case file (see HistoriqueActionService#enregistrer Javadoc).
        Utilisateur auteur = currentUserService.getCurrentUser();
        // The key goes into `details`: concatenated into the sentence, it would produce a distinct
        // label per parameter, i.e. seven action types instead of one.
        historiqueActionService.enregistrerPour(auteur, null, TypeActionHistorique.CONFIGURATION_MODIFIEE,
                Map.of("cle", cle, "ancienneValeur", ancienneValeur, "nouvelleValeur", valeurRetenue));

        return configurationMapper.toResponse(configuration);
    }

    /** Key without a declared rule: a non-empty value is required. */
    private static String exigerNonVide(String cle, String valeur) {
        if (valeur == null || valeur.isBlank()) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Configuration " + cle + " : la valeur est obligatoire");
        }
        return valeur;
    }
}
