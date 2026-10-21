package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.mapper.ClientMapper;
import com.afriland.audiences.referentiel.repository.ClientRepository;
import com.afriland.audiences.referentiel.web.dto.ClientResponse;
import com.afriland.audiences.referentiel.web.dto.CreerClientRequest;
import com.afriland.audiences.referentiel.web.dto.ModifierClientRequest;
import com.afriland.audiences.common.reference.FamilleReference;
import com.afriland.audiences.common.reference.GenerateurReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** M14: `ClientService` unit tests: search, uniqueness, 404. */
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private HistoriqueActionService historiqueActionService;

    private final ClientMapper mapper = new ClientMapper() {
        @Override
        public ClientResponse toResponse(Client client) {
            return new ClientResponse(client.getId(), client.getReference(), client.getNom(),
                    client.getEmail(), client.getTelephone(), client.getCreatedAt(), client.getUpdatedAt());
        }

        @Override
        public Client toEntity(CreerClientRequest request) {
            // The reference is no longer in the request: the service assigns and sets it afterwards.
            return Client.builder().nom(request.nom()).build();
        }
    };

    private ClientService service;
    private Client client;

    @Mock private GenerateurReference generateurReference;

    @BeforeEach
    void setUp() {
        service = new ClientService(clientRepository, mapper, generateurReference, historiqueActionService);
        lenient().when(generateurReference.prochaine(FamilleReference.CLIENT)).thenReturn("CLI-2026-0001");
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

    /**
     * The reference is assigned by the system: a duplicate can only come from an out-of-sync counter
     * (V36). The message says so and names <b>no field</b>, since there is nothing to fix on screen.
     */
    @Test
    void creerClient_signaleUneReferenceAttribueeDejaPortee() {
        when(clientRepository.findByReference("CLI-2026-0001")).thenReturn(Optional.of(client));

        var requete = new CreerClientRequest("Autre", null, null);
        assertThatThrownBy(() -> service.creerClient(requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("désynchronisé")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::getChamp)
                .isNull();
    }

    @Test
    void creerClient_nominal() {
        when(clientRepository.findByReference("CLI-2026-0001")).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        ClientResponse result = service.creerClient(new CreerClientRequest("Nouveau client", null, null));

        assertThat(result.id()).isEqualTo(2L);
        // The reference comes from the generator, never from the request.
        assertThat(result.reference()).isEqualTo("CLI-2026-0001");
        verify(generateurReference).prochaine(FamilleReference.CLIENT);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detailsHistorises() {
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(historiqueActionService).enregistrer(isNull(), eq(TypeActionHistorique.CLIENT_MODIFIE), details.capture());
        return details.getValue();
    }

    @Test
    void modifierClient_metAJourEtNHistoriseQueLesChampsChanges() {
        client.setEmail("ancien@banque.cm");
        client.setTelephone("+237600000000");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        // The phone is entered with spaces but normalizes to the stored value: unchanged.
        ClientResponse result = service.modifierClient(1L,
                new ModifierClientRequest("Banque Test", "nouveau@banque.cm", "+237 600 000 000"));

        assertThat(result.email()).isEqualTo("nouveau@banque.cm");
        // Only the email changed: the identical name and phone do not clutter the trace.
        assertThat(detailsHistorises()).containsExactly(
                Map.entry("reference", "CLI-001"),
                Map.entry("ancienEmail", "ancien@banque.cm"),
                Map.entry("nouvelEmail", "nouveau@banque.cm"));
    }

    @Test
    void modifierClient_neTouchePasALaReference() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponse result = service.modifierClient(1L, new ModifierClientRequest("Nouveau nom", null, null));

        assertThat(result.reference()).isEqualTo("CLI-001");
        assertThat(result.nom()).isEqualTo("Nouveau nom");
    }

    @Test
    void modifierClient_sansAucunChangement_nEcritRien() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));

        // "Banque Test" identical, spaces included: not a modification.
        service.modifierClient(1L, new ModifierClientRequest("  Banque Test ", "", "   "));

        verify(clientRepository, never()).save(any());
        verifyNoInteractions(historiqueActionService);
    }

    @Test
    void modifierClient_unChampVide_devientAbsent() {
        client.setTelephone("+237 600 000 000");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponse result = service.modifierClient(1L, new ModifierClientRequest("Banque Test", null, ""));

        assertThat(result.telephone()).isNull();
        assertThat(detailsHistorises()).containsEntry("ancienTelephone", "+237 600 000 000")
                .containsEntry("nouveauTelephone", null);
    }

    @Test
    void modifierClientInexistant_renvoie404() {
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());

        var requete = new ModifierClientRequest("X", null, null);
        assertThatThrownBy(() -> service.modifierClient(99L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Client introuvable");
        verifyNoInteractions(historiqueActionService);
    }

    // Phone: same rule as an intervenant's (Coordonnees.telephone), checked by the server.

    private ApiException refus(Runnable appel) {
        try {
            appel.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("refus attendu");
    }

    @Test
    void creerClient_telephoneCamerounais_enregistreAuFormatInternational() {
        when(clientRepository.findByReference("CLI-2026-0001")).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponse result = service.creerClient(new CreerClientRequest("Client SA", null, "650 22 89 36"));

        assertThat(result.telephone()).isEqualTo("+237650228936");
    }

    @Test
    void creerClient_sansTelephone_resteAbsent() {
        when(clientRepository.findByReference("CLI-2026-0001")).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.creerClient(new CreerClientRequest("Client SA", null, "   ")).telephone()).isNull();
    }

    @Test
    void creerClient_telephoneHorsFormat_estRefuseSurLeChamp() {
        when(clientRepository.findByReference("CLI-2026-0001")).thenReturn(Optional.empty());
        var fixe = new CreerClientRequest("Client SA", null, "2 22 45 67 89");
        var etrangerSansIndicatif = new CreerClientRequest("Client SA", null, "33612345678");

        ApiException refusFixe = refus(() -> service.creerClient(fixe));
        ApiException refusEtranger = refus(() -> service.creerClient(etrangerSansIndicatif));

        assertThat(refusFixe.getChamp()).isEqualTo("telephone");
        assertThat(refusFixe.getMessage()).contains("Numéro de téléphone invalide");
        assertThat(refusEtranger.getChamp()).isEqualTo("telephone");
        verify(clientRepository, never()).save(any());
    }

    @Test
    void modifierClient_telephoneHorsFormat_estRefuseSansRienEcrire() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        var requete = new ModifierClientRequest("Banque Test", null, "12345");

        assertThat(refus(() -> service.modifierClient(1L, requete)).getChamp()).isEqualTo("telephone");
        verify(clientRepository, never()).save(any());
        verifyNoInteractions(historiqueActionService);
    }

    @Test
    void modifierClient_ancienFormat_estNormaliseEtTrace() {
        client.setTelephone("+237 600 000 000");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponse result = service.modifierClient(1L, new ModifierClientRequest("Banque Test", null, "+237 600 000 000"));

        // A number stored before the rule is rewritten in the single stored format, and the change is traced.
        assertThat(result.telephone()).isEqualTo("+237600000000");
        assertThat(detailsHistorises()).containsEntry("ancienTelephone", "+237 600 000 000")
                .containsEntry("nouveauTelephone", "+237600000000");
    }
}
