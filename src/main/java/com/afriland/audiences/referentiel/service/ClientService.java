package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.mapper.ClientMapper;
import com.afriland.audiences.referentiel.repository.ClientRepository;
import com.afriland.audiences.referentiel.web.dto.ClientResponse;
import com.afriland.audiences.referentiel.web.dto.CreerClientRequest;
import com.afriland.audiences.referentiel.web.dto.ModifierClientRequest;
import com.afriland.audiences.common.reference.FamilleReference;
import com.afriland.audiences.common.reference.GenerateurReference;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;
    private final GenerateurReference generateurReference;
    private final HistoriqueActionService historiqueActionService;

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> rechercherClients(String nom, String reference, Pageable pageable) {
        if (StringUtils.hasText(reference)) {
            return clientRepository.findByReference(reference)
                    .map(client -> new PageResponse<>(java.util.List.of(clientMapper.toResponse(client)), 1, 1L))
                    .orElseGet(() -> new PageResponse<>(java.util.List.of(), 0, 0L));
        }
        var page = clientRepository.findByNomContainingIgnoreCase(nom == null ? "" : nom, pageable);
        return PageResponse.of(page, clientMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ClientResponse consulterClient(Long id) {
        return clientMapper.toResponse(getClientOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Client getClientOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Client introuvable : " + id));
    }

    @Transactional
    public ClientResponse creerClient(CreerClientRequest request) {
        /*
         * La référence est attribuée par le système (métier, 2026-09-29). Le contrôle qui suit a
         * changé de sens : il ne sanctionne plus une saisie — il n'y en a plus — mais détecte un
         * compteur désynchronisé (V36), seul cas où une référence attribuée pourrait être déjà
         * portée. Aucun champ n'est nommé : il n'y en a plus à corriger à l'écran.
         */
        String reference = generateurReference.prochaine(FamilleReference.CLIENT);
        if (clientRepository.findByReference(reference).isPresent()) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "La référence attribuée '" + reference + "' est déjà portée par un client : "
                            + "le compteur de références est désynchronisé (V36)");
        }
        Client client = clientMapper.toEntity(request);
        client.setReference(reference);
        client = clientRepository.save(client);
        return clientMapper.toResponse(client);
    }

    /**
     * Modifie le nom et les coordonnées d'un client. La référence n'est pas modifiable : elle est
     * l'identifiant du client (cf. {@link ModifierClientRequest}).
     *
     * <p>Seuls les champs <b>réellement changés</b> sont historisés, avec leur valeur d'avant et
     * d'après. Une requête qui ne change rien n'écrit aucune ligne : ce n'est pas une modification, et
     * un journal qui en garderait trace mentirait sur ce qui s'est passé.
     */
    @Transactional
    public ClientResponse modifierClient(Long id, ModifierClientRequest request) {
        Client client = getClientOrThrow(id);
        String nom = request.nom().trim();
        String email = videEnNull(request.email());
        String telephone = videEnNull(request.telephone());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reference", client.getReference());
        noterSiChange(details, "ancienNom", "nouveauNom", client.getNom(), nom);
        noterSiChange(details, "ancienEmail", "nouvelEmail", client.getEmail(), email);
        noterSiChange(details, "ancienTelephone", "nouveauTelephone", client.getTelephone(), telephone);

        if (details.size() > 1) {
            client.setNom(nom);
            client.setEmail(email);
            client.setTelephone(telephone);
            client = clientRepository.save(client);
            historiqueActionService.enregistrer(null, TypeActionHistorique.CLIENT_MODIFIE, details);
        }
        return clientMapper.toResponse(client);
    }

    private static void noterSiChange(Map<String, Object> details, String cleAvant, String cleApres,
                                      String avant, String apres) {
        if (!Objects.equals(avant, apres)) {
            details.put(cleAvant, avant);
            details.put(cleApres, apres);
        }
    }

    /** Un champ facultatif vidé à l'écran arrive en chaîne vide : il est enregistré comme absent. */
    private static String videEnNull(String valeur) {
        return StringUtils.hasText(valeur) ? valeur.trim() : null;
    }
}
