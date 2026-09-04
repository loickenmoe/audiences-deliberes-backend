package com.afriland.audiences.dossier.service;

import com.afriland.audiences.common.audit.HistoriqueAction;
import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.AuditSeuilDossier;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.StatutValidation;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.mapper.AuditSeuilMapper;
import com.afriland.audiences.dossier.mapper.DossierMapper;
import com.afriland.audiences.dossier.mapper.EtapeProcedureMapper;
import com.afriland.audiences.dossier.mapper.HistoriqueActionMapper;
import com.afriland.audiences.dossier.repository.AuditSeuilDossierRepository;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.dossier.web.dto.CreerDossierRequest;
import com.afriland.audiences.dossier.web.dto.CreerEtapeRequest;
import com.afriland.audiences.dossier.web.dto.DemandeDerogationSeuilRequest;
import com.afriland.audiences.dossier.web.dto.DossierResponse;
import com.afriland.audiences.dossier.web.dto.ModifierAffectationRequest;
import com.afriland.audiences.dossier.web.dto.ModifierStatutEtapeRequest;
import com.afriland.audiences.dossier.web.dto.ValiderSensibiliteRequest;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.domain.NatureDossier;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.ClientRepository;
import com.afriland.audiences.referentiel.repository.NatureDossierRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DossierServiceTest {

    @Mock private DossierRepository dossierRepository;
    @Mock private EtapeProcedureRepository etapeProcedureRepository;
    @Mock private DossierJuristeRepository dossierJuristeRepository;
    @Mock private DossierAvocatRepository dossierAvocatRepository;
    @Mock private AuditSeuilDossierRepository auditSeuilDossierRepository;
    @Mock private HistoriqueActionRepository historiqueActionRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private AvocatRepository avocatRepository;
    @Mock private NatureDossierRepository natureDossierRepository;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private CurrentUserService currentUserService;

    private final EtapeTransitionValidator transitionValidator = new EtapeTransitionValidator();
    private final DossierMapper dossierMapper = new DossierMapper();
    private final EtapeProcedureMapper etapeProcedureMapper = new EtapeProcedureMapperTestImpl();
    private final AuditSeuilMapper auditSeuilMapper = new AuditSeuilMapperTestImpl();
    private final HistoriqueActionMapper historiqueActionMapper = new HistoriqueActionMapperTestImpl();

    private DossierService service;

    private Client client;
    private Utilisateur juriste;

    @BeforeEach
    void setUp() {
        service = new DossierService(dossierRepository, etapeProcedureRepository, dossierJuristeRepository,
                dossierAvocatRepository, auditSeuilDossierRepository, historiqueActionRepository, clientRepository,
                utilisateurRepository, avocatRepository, natureDossierRepository, transitionValidator, dossierMapper,
                etapeProcedureMapper, auditSeuilMapper, historiqueActionMapper, historiqueActionService, currentUserService);

        client = Client.builder().id(1L).reference("CLI-1").nom("Client Test").build();
        juriste = Utilisateur.builder().id(10L).nom("Juriste").build();
    }

    private CreerDossierRequest baseRequest(boolean sensible) {
        return new CreerDossierRequest(
                "DOS-2026-001", "Saisie immobilière", null, "Tribunal de Yaoundé", 1L,
                null, 15_000_000d, null, null, null, null,
                sensible ? null : "CREDIT-1", sensible ? null : "PV-1",
                null, null, null,
                sensible, sensible ? 60_000_000d : null, sensible ? "VIP" : null,
                List.of(10L), List.of());
    }

    @Test
    void creerDossier_rejectsDuplicateReference() {
        when(dossierRepository.existsByReference("DOS-2026-001")).thenReturn(true);

        assertThatThrownBy(() -> service.creerDossier(baseRequest(false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà utilisée");
    }

    @Test
    void creerDossier_rejectsUnknownClient() {
        when(dossierRepository.existsByReference(any())).thenReturn(false);
        when(clientRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creerDossier(baseRequest(false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Client introuvable");
    }

    @Test
    void creerDossier_rejectsUnknownNature() {
        when(dossierRepository.existsByReference(any())).thenReturn(false);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase("Saisie immobilière")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creerDossier(baseRequest(false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("taxonomie");
    }

    @Test
    void creerDossier_rejectsInconsistentCategorie() {
        when(dossierRepository.existsByReference(any())).thenReturn(false);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase("Saisie immobilière"))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        var incoherent = new CreerDossierRequest(
                "DOS-2026-001", "Saisie immobilière", CategorieDossier.EXPLOITATION_LITIGES, "Tribunal de Yaoundé", 1L,
                null, 15_000_000d, null, null, null, null, "CREDIT-1", "PV-1",
                null, null, null, false, null, null, List.of(10L), List.of());

        assertThatThrownBy(() -> service.creerDossier(incoherent))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("incohérente");
    }

    @Test
    void creerDossier_requiresCreditOrigineAndPvTransfertForRecouvrement() {
        when(dossierRepository.existsByReference(any())).thenReturn(false);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase(any()))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        var sansPieces = new CreerDossierRequest(
                "DOS-2026-001", "Saisie immobilière", null, "Tribunal de Yaoundé", 1L,
                null, 15_000_000d, null, null, null, null, null, null,
                null, null, null, false, null, null, List.of(10L), List.of());

        assertThatThrownBy(() -> service.creerDossier(sansPieces))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("dossierCreditOrigine");
    }

    @Test
    void creerDossier_requiresSeuilAndTypeClientWhenSensible() {
        when(dossierRepository.existsByReference(any())).thenReturn(false);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase(any()))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(utilisateurRepository.findById(10L)).thenReturn(Optional.of(juriste));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> {
            Dossier d = inv.getArgument(0);
            d.setId(99L);
            return d;
        });
        when(etapeProcedureRepository.save(any(EtapeProcedure.class))).thenAnswer(inv -> inv.getArgument(0));
        var sensibleSansSeuil = new CreerDossierRequest(
                "DOS-2026-001", "Saisie immobilière", null, "Tribunal de Yaoundé", 1L,
                null, 15_000_000d, null, null, null, null, "CREDIT-1", "PV-1",
                null, null, null, true, null, null, List.of(10L), List.of());

        assertThatThrownBy(() -> service.creerDossier(sensibleSansSeuil))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("obligatoires pour un dossier sensible");
    }

    @Test
    void creerDossier_happyPath_createsInstanceEtapeAndHistorise() {
        when(dossierRepository.existsByReference(any())).thenReturn(false);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase(any()))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(utilisateurRepository.findById(10L)).thenReturn(Optional.of(juriste));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> {
            Dossier d = inv.getArgument(0);
            d.setId(99L);
            return d;
        });
        when(etapeProcedureRepository.save(any(EtapeProcedure.class))).thenAnswer(inv -> inv.getArgument(0));

        DossierResponse response = service.creerDossier(baseRequest(false));

        assertThat(response.reference()).isEqualTo("DOS-2026-001");
        assertThat(response.etapes()).hasSize(1);
        assertThat(response.etapes().get(0).type()).isEqualTo(TypeEtape.INSTANCE);
        assertThat(response.etapes().get(0).statutCycleVie()).isEqualTo(StatutCycleVie.OUVERTURE);
    }

    @Test
    void modifierStatutEtape_rejectsUnauthorisedTransition() {
        Dossier dossier = Dossier.builder().id(1L).build();
        EtapeProcedure etape = EtapeProcedure.builder().id(5L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.OUVERTURE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(5L)).thenReturn(Optional.of(etape));

        assertThatThrownBy(() -> service.modifierStatutEtape(1L, 5L,
                new ModifierStatutEtapeRequest(StatutCycleVie.CLOTURE)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("non autorisée");
    }

    @Test
    void modifierStatutEtape_recoursOnRecours2_rejectedNoFurtherRecours() {
        Dossier dossier = Dossier.builder().id(1L).build();
        EtapeProcedure etape = EtapeProcedure.builder().id(5L).dossier(dossier).type(TypeEtape.RECOURS_2)
                .statutCycleVie(StatutCycleVie.DELIBERE_VIDE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(5L)).thenReturn(Optional.of(etape));
        when(etapeProcedureRepository.save(any(EtapeProcedure.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.modifierStatutEtape(1L, 5L,
                new ModifierStatutEtapeRequest(StatutCycleVie.EN_COURS)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucune voie de recours");
    }

    @Test
    void modifierAffectation_rejectsDuplicateWithoutForcer() {
        Dossier dossier = Dossier.builder().id(1L).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(List.of());
        when(dossierAvocatRepository.findByDossierId(1L))
                .thenReturn(List.of(com.afriland.audiences.dossier.domain.DossierAvocat.builder()
                        .avocat(com.afriland.audiences.referentiel.domain.Avocat.builder().id(7L).build())
                        .build()));

        assertThatThrownBy(() -> service.modifierAffectation(1L,
                new ModifierAffectationRequest(List.of(), List.of(7L)), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà affecté");
    }

    @Test
    void demanderDerogationSeuil_rejectsWhenDossierNotYetValidatedSensitive() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(false)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.demanderDerogationSeuil(1L,
                new DemandeDerogationSeuilRequest(80_000_000d, "Motif")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("sensible déjà validé");
    }

    @Test
    void validerSensibilite_rejectsWhenNoPendingProposal() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(false)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.validerSensibilite(1L,
                new ValiderSensibiliteRequest(StatutValidation.VALIDEE, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucune proposition");
    }

    @Test
    void validerSensibilite_rejectsRejetWithoutMotif() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(true)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.validerSensibilite(1L,
                new ValiderSensibiliteRequest(StatutValidation.REJETEE, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("motif est obligatoire");
    }

    @Test
    void creerEtape_rejectsWhenEtapeAlreadyExists() {
        Dossier dossier = Dossier.builder().id(1L).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.existsByDossierIdAndType(1L, TypeEtape.INSTANCE)).thenReturn(true);

        assertThatThrownBy(() -> service.creerEtape(1L, new CreerEtapeRequest(TypeEtape.INSTANCE)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void creerEtape_directPositioningOnRecours2CreatesIntermediateEtapes() {
        Dossier dossier = Dossier.builder().id(1L).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.existsByDossierIdAndType(anyLong(), any())).thenReturn(false);
        // GenerationType.IDENTITY : l'id est toujours peuplé après un save() réel ; le stub le
        // simule pour rester réaliste (des appels en aval, ex. l'historisation, en dépendent).
        long[] nextId = {1L};
        when(etapeProcedureRepository.save(any(EtapeProcedure.class))).thenAnswer(inv -> {
            EtapeProcedure e = inv.getArgument(0);
            e.setId(nextId[0]++);
            return e;
        });

        service.creerEtape(1L, new CreerEtapeRequest(TypeEtape.RECOURS_2));

        org.mockito.Mockito.verify(etapeProcedureRepository, org.mockito.Mockito.times(3))
                .save(any(EtapeProcedure.class));
    }

    // ---- Mappers minimaux, sans dépendre du bytecode généré par l'annotation processor MapStruct ----

    private static class EtapeProcedureMapperTestImpl implements EtapeProcedureMapper {
        @Override
        public com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse toResponse(EtapeProcedure etape) {
            return new com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse(
                    etape.getId(), etape.getDossier() != null ? etape.getDossier().getId() : null,
                    etape.getType(), etape.getStatutCycleVie());
        }
    }

    private static class AuditSeuilMapperTestImpl implements AuditSeuilMapper {
        @Override
        public com.afriland.audiences.dossier.web.dto.AuditSeuilResponse toResponse(AuditSeuilDossier audit) {
            return new com.afriland.audiences.dossier.web.dto.AuditSeuilResponse(
                    audit.getId(), audit.getDossier().getId(), audit.getUtilisateur().getId(),
                    audit.getAncienSeuil(), audit.getNouveauSeuil(), audit.getMotif(),
                    audit.getValidePar() != null ? audit.getValidePar().getId() : null,
                    audit.getStatut(), audit.getDateAction());
        }
    }

    private static class HistoriqueActionMapperTestImpl implements HistoriqueActionMapper {
        @Override
        public com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse toResponse(HistoriqueAction action) {
            return new com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse(
                    action.getId(), action.getUtilisateur() != null ? action.getUtilisateur().getId() : null,
                    action.getAction(), action.getDetails(), action.getDateAction());
        }
    }
}
