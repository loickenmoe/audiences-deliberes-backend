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
 * Paramètres système modifiables par le DJ (CONF01-07, US §11.2). {@code GET /configurations},
 * {@code PUT /configurations/{cle}} — cf. API_IMPLEMENTATION_STATUS.md §9 (#47, #48).
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
        return configurationSystemeRepository.findByCle(cle)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Clé de configuration introuvable : " + cle));
    }

    /**
     * Modifie un paramètre. La valeur est contrôlée puis normalisée selon la règle de la clé
     * (Q-91) : sans ce garde-fou, une saisie erronée du DJ ne se manifestait qu'ailleurs, en
     * erreur 500, au moment où un autre module reconvertit la valeur.
     */
    @Transactional
    public ConfigurationResponse modifier(String cle, String nouvelleValeur) {
        ConfigurationSysteme configuration = getOrThrow(cle);
        String valeurRetenue = RegleConfiguration.pour(cle)
                .map(regle -> regle.valider(nouvelleValeur))
                .orElse(nouvelleValeur);
        String ancienneValeur = configuration.getValeur();
        configuration.setValeur(valeurRetenue);
        configuration = configurationSystemeRepository.save(configuration);

        // dossierId=null : action hors dossier (cf. Javadoc HistoriqueActionService#enregistrer).
        Utilisateur auteur = currentUserService.getCurrentUser();
        // Q-111 : la clé descend dans `details`. Concaténée dans la phrase, elle produisait un
        // libellé distinct par paramètre — sept natures d'action là où il n'y en a qu'une.
        historiqueActionService.enregistrerPour(auteur, null, TypeActionHistorique.CONFIGURATION_MODIFIEE,
                Map.of("cle", cle, "ancienneValeur", ancienneValeur, "nouvelleValeur", valeurRetenue));

        return configurationMapper.toResponse(configuration);
    }
}
