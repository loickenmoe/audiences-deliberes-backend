package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.mapper.ClientMapper;
import com.afriland.audiences.referentiel.repository.ClientRepository;
import com.afriland.audiences.referentiel.web.dto.ClientResponse;
import com.afriland.audiences.referentiel.web.dto.CreerClientRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

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
        if (clientRepository.findByReference(request.reference()).isPresent()) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "La référence client '" + request.reference() + "' est déjà utilisée");
        }
        Client client = clientMapper.toEntity(request);
        client = clientRepository.save(client);
        return clientMapper.toResponse(client);
    }
}
