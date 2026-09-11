package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.mapper.ClientMapper;
import com.afriland.audiences.referentiel.repository.ClientRepository;
import com.afriland.audiences.referentiel.web.dto.ClientResponse;
import com.afriland.audiences.referentiel.web.dto.CreerClientRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** M14 — `ClientService` (M4) n'avait aucun test unitaire dédié : recherche, unicité, 404. */
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock private ClientRepository clientRepository;

    private final ClientMapper mapper = new ClientMapper() {
        @Override
        public ClientResponse toResponse(Client client) {
            return new ClientResponse(client.getId(), client.getReference(), client.getNom(),
                    client.getEmail(), client.getTelephone(), client.getCreatedAt(), client.getUpdatedAt());
        }

        @Override
        public Client toEntity(CreerClientRequest request) {
            return Client.builder().reference(request.reference()).nom(request.nom()).build();
        }
    };

    private ClientService service;
    private Client client;

    @BeforeEach
    void setUp() {
        service = new ClientService(clientRepository, mapper);
        client = Client.builder().id(1L).reference("CLI-001").nom("Banque Test").build();
    }

    @Test
    void rechercherParReference_retourneLUniqueResultat() {
        when(clientRepository.findByReference("CLI-001")).thenReturn(Optional.of(client));

        PageResponse<ClientResponse> result = service.rechercherClients(null, "CLI-001", PageRequest.of(0, 20));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).reference()).isEqualTo("CLI-001");
    }

    @Test
    void rechercherParReferenceInconnue_retourneUnePageVide() {
        when(clientRepository.findByReference("CLI-404")).thenReturn(Optional.empty());

        PageResponse<ClientResponse> result = service.rechercherClients(null, "CLI-404", PageRequest.of(0, 20));

        assertThat(result.totalElements()).isZero();
        assertThat(result.content()).isEmpty();
    }

    @Test
    void rechercherParNom_delegueAuRepositoryPagine() {
        when(clientRepository.findByNomContainingIgnoreCase(eq("banque"), any()))
                .thenReturn(new PageImpl<>(List.of(client)));

        PageResponse<ClientResponse> result = service.rechercherClients("banque", null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void rechercherSansCritere_utiliseUneChaineVide() {
        when(clientRepository.findByNomContainingIgnoreCase(eq(""), any())).thenReturn(new PageImpl<>(List.of(client)));

        assertThat(service.rechercherClients(null, null, PageRequest.of(0, 20)).content()).hasSize(1);
    }

    @Test
    void consulterClientInexistant_renvoie404() {
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consulterClient(99L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Client introuvable");
    }

    @Test
    void creerClient_rejetteUneReferenceDejaUtilisee() {
        when(clientRepository.findByReference("CLI-001")).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.creerClient(new CreerClientRequest("CLI-001", "Autre", null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà utilisée")
                // ERR-CONFLICT couvre une dizaine de conflits : sans ce champ, un client ne peut pas
                // savoir lequel des siens signaler à l'utilisateur.
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::getChamp)
                .isEqualTo("reference");
    }

    @Test
    void creerClient_nominal() {
        when(clientRepository.findByReference("CLI-002")).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        ClientResponse result = service.creerClient(new CreerClientRequest("CLI-002", "Nouveau client", null, null));

        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.reference()).isEqualTo("CLI-002");
    }
}
