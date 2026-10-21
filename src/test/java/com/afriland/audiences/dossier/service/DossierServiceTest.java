package com.afriland.audiences.dossier.service;

import org.springframework.data.domain.PageRequest;
import java.time.LocalDate;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.afriland.audiences.dossier.web.dto.AuditSeuilResponse;
import com.afriland.audiences.dossier.web.dto.ValiderDerogationRequest;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueAction;
import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.reference.FamilleReference;
import com.afriland.audiences.common.reference.GenerateurReference;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.AuditSeuilDossier;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.SeuilOrigine;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.StatutValidation;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.mapper.AuditSeuilMapper;
import com.afriland.audiences.dossier.mapper.DossierMapper;
import com.afriland.audiences.dossier.mapper.EtapeProcedureMapper;
import com.afriland.audiences.dossier.mapper.HistoriqueActionMapper;
import com.afriland.audiences.dossier.repository.AuditSeuilDossierRepository;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.dossier.web.dto.CreerDossierRequest;
import com.afriland.audiences.dossier.web.dto.CreerEtapeRequest;
import com.afriland.audiences.dossier.web.dto.DemandeDerogationSeuilRequest;
import com.afriland.audiences.dossier.web.dto.DossierResponse;
import com.afriland.audiences.dossier.web.dto.ModifierAffectationRequest;
import com.afriland.audiences.dossier.web.dto.ModifierSensibiliteRequest;
import com.afriland.audiences.dossier.web.dto.ProposerSensibiliteRequest;
import com.afriland.audiences.dossier.web.dto.PartiesDto;
import com.afriland.audiences.dossier.web.dto.ModifierStatutEtapeRequest;
import com.afriland.audiences.dossier.web.dto.ValiderSensibiliteRequest;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.domain.NatureDossier;
import com.afriland.audiences.referentiel.domain.TypeClientSensible;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.service.AgenceService;
import com.afriland.audiences.referentiel.repository.ClientRepository;
import com.afriland.audiences.referentiel.repository.NatureDossierRepository;
import com.afriland.audiences.referentiel.repository.TypeClientSensibleRepository;
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
    @Mock private TypeClientSensibleRepository typeClientSensibleRepository;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private CurrentUserService currentUserService;
    @Mock private AlerteService alerteService;
    @Mock private AgenceService agenceService;
    @Mock private GenerateurReference generateurReference;

    private final EtapeTransitionValidator transitionValidator = new EtapeTransitionValidator();
    private final DossierMapper dossierMapper = new DossierMapper();
    private final EtapeProcedureMapper etapeProcedureMapper = new EtapeProcedureMapperTestImpl();
    private final AuditSeuilMapper auditSeuilMapper = new AuditSeuilMapperTestImpl();
    private final HistoriqueActionMapper historiqueActionMapper = new HistoriqueActionMapperTestImpl();

    /** Bonanjo: a real agency from the reference data (V42), so no code is invented. */
    private static final String AGENCE = "00002";

    /** Parties are mandatory. */
    private static final PartiesDto PARTIES =
            new PartiesDto(List.of("Afriland First Bank"), List.of("Client Test"));

    private DossierService service;

    private Client client;
    private Utilisateur juriste;

    @Mock
    private NotificationPrestataireService notificationPrestataireService;

    @BeforeEach
    void setUp() {
        service = new DossierService(dossierRepository, etapeProcedureRepository, dossierJuristeRepository,
                dossierAvocatRepository, auditSeuilDossierRepository, historiqueActionRepository, clientRepository,
                utilisateurRepository, avocatRepository, notificationPrestataireService,
                natureDossierRepository, typeClientSensibleRepository,
                transitionValidator, dossierMapper,
                etapeProcedureMapper, auditSeuilMapper, historiqueActionMapper, historiqueActionService, currentUserService,
                alerteService, agenceService, generateurReference, List.of());

        // The system assigns the reference: tests no longer provide one. `lenient` because most tests
        // of this class never create a case.
        lenient().when(generateurReference.prochaine(FamilleReference.DOSSIER)).thenReturn("DOS-2026-0001");

        // The originating agency is mandatory and its code is checked against the reference data.
        // `lenient` for the same reason as the reference.
        lenient().when(agenceService.existeEtEstActive(AGENCE)).thenReturn(true);

        client = Client.builder().id(1L).reference("CLI-1").nom("Client Test").build();
        juriste = Utilisateur.builder().id(10L).nom("Juriste").build();
    }

    private CreerDossierRequest baseRequest(boolean sensible) {
        return new CreerDossierRequest(
                "Saisie immobilière", null, "Tribunal de Yaoundé", 1L,
                PARTIES, 15_000_000d, null, null, AGENCE, null,
                sensible ? null : "CREDIT-1", sensible ? null : "PV-1",
                null, null, null,
                sensible, sensible ? 60_000_000d : null, sensible ? "VIP" : null,
                List.of(10L), List.of());
    }

    /**
     * The reference is assigned by the system, so a duplicate cannot come from user input. If it occurs,
     * the counter (V36) is out of sync: the message must say so and name <b>no field</b>.
     */
    @Test
    void creerDossier_signaleUneReferenceAttribueeDejaPortee() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase(any()))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        when(dossierRepository.existsByReference("DOS-2026-0001")).thenReturn(true);

        var requete = baseRequest(false);
        assertThatThrownBy(() -> service.creerDossier(requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("désynchronisé")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::getChamp)
                .isNull();
    }

    /** The reference does not come from the request: it is requested from the generator. */
    @Test
    void creerDossier_attribueLaReferenceLuiMeme() {
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

        assertThat(response.reference()).isEqualTo("DOS-2026-0001");
        verify(generateurReference).prochaine(FamilleReference.DOSSIER);
    }

    @Test
    void creerDossier_rejectsUnknownClient() {
        when(clientRepository.findById(1L)).thenReturn(Optional.empty());

        var requete = baseRequest(false);
        assertThatThrownBy(() -> service.creerDossier(requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Client introuvable");
    }

    @Test
    void creerDossier_rejectsUnknownNature() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase("Saisie immobilière")).thenReturn(Optional.empty());

        var requete = baseRequest(false);
        assertThatThrownBy(() -> service.creerDossier(requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("taxonomie");
    }

    @Test
    void creerDossier_rejectsInconsistentCategorie() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase("Saisie immobilière"))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        var incoherent = new CreerDossierRequest(
                "Saisie immobilière", CategorieDossier.EXPLOITATION_LITIGES, "Tribunal de Yaoundé", 1L,
                PARTIES, 15_000_000d, null, null, AGENCE, null, "CREDIT-1", "PV-1",
                null, null, null, false, null, null, List.of(10L), List.of());

        assertThatThrownBy(() -> service.creerDossier(incoherent))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("incohérente");
    }

    /**
     * Attachments are mandatory, not their associated text: a recovery case is created without its
     * references. The obligation applies to files and is enforced by the UI, since a document can only
     * be uploaded after creation produces the case identifier.
     */
    @Test
    void creerDossier_accepteUnRecouvrementSansSesReferences() {
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
        var sansReferences = new CreerDossierRequest(
                "Saisie immobilière", null, "Tribunal de Yaoundé", 1L,
                PARTIES, 15_000_000d, null, null, AGENCE, null, null, null,
                null, null, null, false, null, null, List.of(10L), List.of());

        DossierResponse response = service.creerDossier(sansReferences);

        assertThat(response.reference()).isEqualTo("DOS-2026-0001");
        assertThat(response.dossierCreditOrigine()).isNull();
        assertThat(response.pvTransfert()).isNull();
    }

    /**
     * Agency codes cannot be invented. The refusal must name the faulty field: the form carries two
     * agency codes, and the user would not know which one to fix.
     */
    @Test
    void creerDossier_rejetteUnCodeAgenceInconnu() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase(any()))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        when(agenceService.existeEtEstActive("5466546546")).thenReturn(false);

        var codeInvente = new CreerDossierRequest(
                "Saisie immobilière", null, "Tribunal de Yaoundé", 1L,
                PARTIES, 15_000_000d, null, null, "5466546546", null, "CREDIT-1", "PV-1",
                null, null, null, false, null, null, List.of(10L), List.of());

        assertThatThrownBy(() -> service.creerDossier(codeInvente))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucune agence ouverte")
                .hasMessageContaining("codeAgenceOrigine");

        // The refusal happens before any write: no reference consumed, no case saved.
        verify(generateurReference, never()).prochaine(any());
        verify(dossierRepository, never()).save(any());
    }

    /**
     * The litigation agency, when provided, goes through the same check. It stays optional (a case is
     * not always transferred), but a wrong code would be counted nowhere in agency reports.
     */
    @Test
    void creerDossier_rejetteUnCodeAgenceContentieuseInconnu() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase(any()))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        when(agenceService.existeEtEstActive("00000000")).thenReturn(false);

        var contentieuseInvente = new CreerDossierRequest(
                "Saisie immobilière", null, "Tribunal de Yaoundé", 1L,
                PARTIES, 15_000_000d, null, null, AGENCE, "00000000", "CREDIT-1", "PV-1",
                null, null, null, false, null, null, List.of(10L), List.of());

        assertThatThrownBy(() -> service.creerDossier(contentieuseInvente))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("codeAgenceContentieuse");

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void creerDossier_rejetteUnTypeClientSensibleHorsReferentiel() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase(any()))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        when(typeClientSensibleRepository.findByCodeIgnoreCase("VIPP")).thenReturn(Optional.empty());

        var faute = new CreerDossierRequest(
                "Saisie immobilière", null, "Tribunal de Yaoundé", 1L,
                PARTIES, 15_000_000d, null, null, AGENCE, null, "CREDIT-1", "PV-1",
                null, null, null, true, 60_000_000d, "VIPP", List.of(10L), List.of());

        // The column has no foreign key; the service performs this check, otherwise a typo would be
        // saved silently and show up in reports.
        assertThatThrownBy(() -> service.creerDossier(faute))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Type de client inconnu");
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
                "Saisie immobilière", null, "Tribunal de Yaoundé", 1L,
                PARTIES, 15_000_000d, null, null, AGENCE, null, "CREDIT-1", "PV-1",
                null, null, null, true, null, null, List.of(10L), List.of());

        assertThatThrownBy(() -> service.creerDossier(sensibleSansSeuil))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("obligatoires pour un dossier à diligence particulière");
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

        assertThat(response.reference()).isEqualTo("DOS-2026-0001");
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

        var requete = new ModifierStatutEtapeRequest(StatutCycleVie.CLOTURE);
        assertThatThrownBy(() -> service.modifierStatutEtape(1L, 5L, requete))
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

        var requete = new ModifierStatutEtapeRequest(StatutCycleVie.EN_COURS);
        assertThatThrownBy(() -> service.modifierStatutEtape(1L, 5L, requete))
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

        var requete = new ModifierAffectationRequest(List.of(), List.of(7L));
        assertThatThrownBy(() -> service.modifierAffectation(1L, requete, false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà affecté");
    }

    @Test
    void modifierAffectation_confirmedDuplicateAlertsEachActiveDj() {
        Dossier dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        Utilisateur dj = Utilisateur.builder().id(20L).nom("DJ").build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(List.of());
        when(dossierAvocatRepository.findByDossierId(1L))
                .thenReturn(List.of(com.afriland.audiences.dossier.domain.DossierAvocat.builder()
                        .avocat(com.afriland.audiences.referentiel.domain.Avocat.builder().id(7L).build())
                        .build()));
        when(avocatRepository.findById(7L))
                .thenReturn(Optional.of(com.afriland.audiences.referentiel.domain.Avocat.builder().id(7L).build()));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(dj));

        service.modifierAffectation(1L, new ModifierAffectationRequest(List.of(), List.of(7L)), true);

        org.mockito.Mockito.verify(alerteService)
                .declencher(org.mockito.ArgumentMatchers.eq(TypeAlerte.DOUBLON_AFFECTATION),
                        org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(dj),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void validerDerogationSeuil_refuseDeTrancherLaPropositionInitiale() {
        // Sensitivity still pending: the only pending audit is the initial proposal.
        Dossier dossier = Dossier.builder().id(1L).estSensible(true)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        var requete = new ValiderDerogationRequest(StatutValidation.VALIDEE, null);
        assertThatThrownBy(() -> service.validerDerogationSeuil(1L, 5L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("validation de la diligence particulière");
        verify(auditSeuilDossierRepository, never()).save(any());
    }

    @Test
    void listerDerogationsSeuil_filtreSurLeStatutDemande() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(Dossier.builder().id(1L).build()));
        when(auditSeuilDossierRepository.findByDossierIdAndStatutOrderByDateActionDesc(1L, StatutValidation.EN_ATTENTE))
                // An audit is always linked to a case and its author: the mapper relies on it.
                .thenReturn(List.of(AuditSeuilDossier.builder().id(9L).dossier(Dossier.builder().id(1L).build())
                        .utilisateur(juriste).nouveauSeuil(80_000_000d)
                        .motif("Client stratégique").statut(StatutValidation.EN_ATTENTE).build()));

        var demandes = service.listerDerogationsSeuil(1L, StatutValidation.EN_ATTENTE);

        assertThat(demandes).extracting(AuditSeuilResponse::id).containsExactly(9L);
        verify(auditSeuilDossierRepository, never()).findByDossierIdOrderByDateActionDesc(any());
    }

    @Test
    void demanderDerogationSeuil_rejectsWhenDossierNotYetValidatedSensitive() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(false)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        var requete = new DemandeDerogationSeuilRequest(80_000_000d, "Motif");
        assertThatThrownBy(() -> service.demanderDerogationSeuil(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("diligence particulière déjà validé");
    }

    /**
     * N4: sensitivity can be proposed after opening, not only at creation.
     *
     * <p>The proposal follows the creation workflow ({@code EN_ATTENTE} plus a threshold proposal), so
     * arbitration does not need to know where it comes from.
     */
    @Test
    void proposerSensibilite_ouvreLeCircuitDeValidationSansToucherAuSeuil() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(false)
                .seuilMontant(null).seuilOrigine(SeuilOrigine.GLOBAL)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(appel -> appel.getArgument(0));
        when(typeClientSensibleRepository.findByCodeIgnoreCase("VIP"))
                .thenReturn(Optional.of(TypeClientSensible.builder().code("VIP").build()));
        when(auditSeuilDossierRepository.save(any(AuditSeuilDossier.class)))
                .thenAnswer(appel -> appel.getArgument(0));
        when(etapeProcedureRepository.findByDossierIdOrderByTypeAsc(1L)).thenReturn(List.of());

        service.proposerSensibilite(1L,
                new ProposerSensibiliteRequest(60_000_000d, "VIP", "La contrepartie est devenue une personne exposée"));

        assertThat(dossier.getEstSensible()).isTrue();
        assertThat(dossier.getSensibiliteStatut()).isEqualTo(StatutValidation.EN_ATTENTE);
        assertThat(dossier.getTypeClientSensible()).isEqualTo("VIP");
        /*
         * No effect before validation: the threshold does not move, otherwise a legal officer alone would
         * trigger double validation of fees.
         */
        assertThat(dossier.getSeuilMontant()).isNull();
        assertThat(dossier.getSeuilOrigine()).isEqualTo(SeuilOrigine.GLOBAL);
        // The threshold proposal is created: arbitration will find it.
        verify(auditSeuilDossierRepository).save(any(AuditSeuilDossier.class));
    }

    /** Proposing twice makes no sense, and the refusal must state which of the two situations applies. */
    @Test
    void proposerSensibilite_refuseUneSecondePropositionEnAttente() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(true)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        var requete = new ProposerSensibiliteRequest(60_000_000d, "VIP", "Motif");
        assertThatThrownBy(() -> service.proposerSensibilite(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà en attente");
        verify(dossierRepository, never()).save(any());
    }

    @Test
    void proposerSensibilite_refuseUnDossierDejaSuiviEnDiligenceParticuliere() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(true)
                .sensibiliteStatut(StatutValidation.VALIDEE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        var requete = new ProposerSensibiliteRequest(60_000_000d, "VIP", "Motif");
        assertThatThrownBy(() -> service.proposerSensibilite(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà suivi");
    }

    /**
     * A case whose proposal was **rejected** can be proposed again. The previous reason is cleared so the
     * screen does not show an outdated decision.
     */
    @Test
    void proposerSensibilite_accepteUnDossierDontLaPropositionAvaitEteRejetee() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(false)
                .sensibiliteStatut(StatutValidation.REJETEE)
                .sensibiliteMotifRejet("Montant insuffisant à l'époque")
                .seuilOrigine(SeuilOrigine.GLOBAL).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(appel -> appel.getArgument(0));
        when(typeClientSensibleRepository.findByCodeIgnoreCase("VIP"))
                .thenReturn(Optional.of(TypeClientSensible.builder().code("VIP").build()));
        when(auditSeuilDossierRepository.save(any(AuditSeuilDossier.class)))
                .thenAnswer(appel -> appel.getArgument(0));
        when(etapeProcedureRepository.findByDossierIdOrderByTypeAsc(1L)).thenReturn(List.of());

        service.proposerSensibilite(1L,
                new ProposerSensibiliteRequest(90_000_000d, "VIP", "Le risque a doublé"));

        assertThat(dossier.getSensibiliteStatut()).isEqualTo(StatutValidation.EN_ATTENTE);
        assertThat(dossier.getSensibiliteMotifRejet()).isNull();
    }

    /** The DJ decides without a proposal: arbitrating their own decision would add nothing. */
    @Test
    void modifierSensibilite_poseLaDiligenceDirectementEtApplique() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(false)
                .seuilOrigine(SeuilOrigine.GLOBAL)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(appel -> appel.getArgument(0));
        when(typeClientSensibleRepository.findByCodeIgnoreCase("VIP"))
                .thenReturn(Optional.of(TypeClientSensible.builder().code("VIP").build()));
        when(auditSeuilDossierRepository
                .findFirstByDossierIdAndStatutOrderByDateActionAsc(1L, StatutValidation.EN_ATTENTE))
                .thenReturn(Optional.empty());
        when(etapeProcedureRepository.findByDossierIdOrderByTypeAsc(1L)).thenReturn(List.of());

        service.modifierSensibilite(1L,
                new ModifierSensibiliteRequest(true, 60_000_000d, "VIP", "Décision du comité"));

        assertThat(dossier.getEstSensible()).isTrue();
        // Directly **validated**: nobody else to wait for.
        assertThat(dossier.getSensibiliteStatut()).isEqualTo(StatutValidation.VALIDEE);
        assertThat(dossier.getSeuilMontant()).isEqualTo(60_000_000d);
        assertThat(dossier.getSeuilOrigine()).isEqualTo(SeuilOrigine.DEROGATION);
    }

    /**
     * Withdrawal must also **restore the global threshold**. A derogation threshold only exists for a
     * sensitive case; keeping it would leave a threshold nobody can revise, since
     * {@code demanderDerogationSeuil} refuses a non-sensitive case.
     */
    @Test
    void modifierSensibilite_retireLaDiligenceEtRendLeSeuilGlobal() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(true)
                .sensibiliteStatut(StatutValidation.VALIDEE)
                .seuilMontant(60_000_000d).seuilOrigine(SeuilOrigine.DEROGATION).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(appel -> appel.getArgument(0));
        when(auditSeuilDossierRepository
                .findFirstByDossierIdAndStatutOrderByDateActionAsc(1L, StatutValidation.EN_ATTENTE))
                .thenReturn(Optional.empty());
        when(etapeProcedureRepository.findByDossierIdOrderByTypeAsc(1L)).thenReturn(List.of());

        service.modifierSensibilite(1L,
                new ModifierSensibiliteRequest(false, null, null, "L'affaire s'est révélée ordinaire"));

        assertThat(dossier.getEstSensible()).isFalse();
        assertThat(dossier.getSensibiliteMotifRetrait()).isEqualTo("L'affaire s'est révélée ordinaire");
        assertThat(dossier.getSeuilMontant()).isNull();
        assertThat(dossier.getSeuilOrigine()).isEqualTo(SeuilOrigine.GLOBAL);
        // The withdrawal reason and the rejection reason are distinct facts: the latter stays empty.
        assertThat(dossier.getSensibiliteMotifRejet()).isNull();
    }

    /** Removing sensitivity takes the case out of double fee validation: the audit will ask why. */
    @Test
    void modifierSensibilite_exigeUnMotifPourRetirer() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(true)
                .sensibiliteStatut(StatutValidation.VALIDEE)
                .seuilMontant(60_000_000d).seuilOrigine(SeuilOrigine.DEROGATION).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        var requete = new ModifierSensibiliteRequest(false, null, null, "   ");
        assertThatThrownBy(() -> service.modifierSensibilite(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("motif est obligatoire");
        verify(dossierRepository, never()).save(any());
    }

    @Test
    void modifierSensibilite_refuseDeRetirerCeQuiNExistePas() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(false)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        var requete = new ModifierSensibiliteRequest(false, null, null, "Motif");
        assertThatThrownBy(() -> service.modifierSensibilite(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("rien à retirer");
    }

    /** Setting sensitivity without threshold or type is meaningless: they make it effective. */
    @Test
    void modifierSensibilite_exigeSeuilEtTypePourPoser() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(
                Dossier.builder().id(1L).estSensible(false)
                        .sensibiliteStatut(StatutValidation.EN_ATTENTE).build()));

        var requete = new ModifierSensibiliteRequest(true, null, null, null);
        assertThatThrownBy(() -> service.modifierSensibilite(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("obligatoires pour un dossier à diligence particulière");
    }

    /**
     * A pending legal officer proposal must not survive the DJ's decision: setting it directly settles it,
     * and the threshold is the one given by the DJ, not the proposed one.
     */
    @Test
    void modifierSensibilite_trancheLaPropositionPendante() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(true)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE)
                .seuilOrigine(SeuilOrigine.GLOBAL).build();
        AuditSeuilDossier pendante = AuditSeuilDossier.builder().id(7L).dossier(dossier)
                .utilisateur(juriste).nouveauSeuil(40_000_000d).motif("Proposition du juriste")
                .statut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(appel -> appel.getArgument(0));
        when(typeClientSensibleRepository.findByCodeIgnoreCase("VIP"))
                .thenReturn(Optional.of(TypeClientSensible.builder().code("VIP").build()));
        when(auditSeuilDossierRepository
                .findFirstByDossierIdAndStatutOrderByDateActionAsc(1L, StatutValidation.EN_ATTENTE))
                .thenReturn(Optional.of(pendante));
        when(auditSeuilDossierRepository.save(any(AuditSeuilDossier.class)))
                .thenAnswer(appel -> appel.getArgument(0));
        when(etapeProcedureRepository.findByDossierIdOrderByTypeAsc(1L)).thenReturn(List.of());

        service.modifierSensibilite(1L,
                new ModifierSensibiliteRequest(true, 75_000_000d, "VIP", "Seuil relevé par le comité"));

        assertThat(pendante.getStatut()).isEqualTo(StatutValidation.VALIDEE);
        assertThat(pendante.getNouveauSeuil()).isEqualTo(75_000_000d);
        assertThat(dossier.getSeuilMontant()).isEqualTo(75_000_000d);
    }

    @Test
    void validerSensibilite_rejectsWhenNoPendingProposal() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(false)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        var requete = new ValiderSensibiliteRequest(StatutValidation.VALIDEE, null, null, null);
        assertThatThrownBy(() -> service.validerSensibilite(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucune proposition");
    }

    @Test
    void validerSensibilite_rejectsRejetWithoutMotif() {
        Dossier dossier = Dossier.builder().id(1L).estSensible(true)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        var requete = new ValiderSensibiliteRequest(StatutValidation.REJETEE, null, null, null);
        assertThatThrownBy(() -> service.validerSensibilite(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("motif est obligatoire");
    }

    @Test
    void creerEtape_rejectsWhenEtapeAlreadyExists() {
        Dossier dossier = Dossier.builder().id(1L).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.existsByDossierIdAndType(1L, TypeEtape.INSTANCE)).thenReturn(true);

        var requete = new CreerEtapeRequest(TypeEtape.INSTANCE);
        assertThatThrownBy(() -> service.creerEtape(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void creerEtape_directPositioningOnRecours2CreatesIntermediateEtapes() {
        Dossier dossier = Dossier.builder().id(1L).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.existsByDossierIdAndType(anyLong(), any())).thenReturn(false);
        // GenerationType.IDENTITY: the id is always set after a real save(); the stub mimics it because
        // downstream calls (e.g. history logging) depend on it.
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

    // Minimal mappers, independent of the MapStruct-generated bytecode.

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
                    action.getCode(), action.getAction(), action.getDetails(), action.getDateAction());
        }
    }

    // Refusals and missing resources: each check below answers with its own message, before any write.

    private Dossier dossierSensible(StatutValidation statut) {
        return Dossier.builder().id(5L).reference("DOS-2026-0005").client(client)
                .estSensible(true).sensibiliteStatut(statut).build();
    }

    @Test
    void lister_fenetreInversee_estRefusee() {
        FiltresDossiers filtres = new FiltresDossiers(null, null, null, null, null, null, null, null, null,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> service.listerDossiers(filtres, false, PageRequest.of(0, 10)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("La date de début doit précéder la date de fin");
    }

    @Test
    void historique_dossierInexistant() {
        when(dossierRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.historique(404L, PageRequest.of(0, 10)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Dossier introuvable : 404");
    }

    @Test
    void vueClient_clientInexistant() {
        when(clientRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.vueConsolideeParClient(404L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Client introuvable : 404");
    }

    @Test
    void affectation_enDouble_demandeConfirmation() {
        when(dossierRepository.findById(5L)).thenReturn(Optional.of(dossierSensible(StatutValidation.VALIDEE)));
        when(dossierJuristeRepository.findByDossierId(5L))
                .thenReturn(List.of(DossierJuriste.builder().utilisateur(juriste).build()));
        when(dossierAvocatRepository.findByDossierId(5L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.modifierAffectation(5L, new ModifierAffectationRequest(List.of(10L), null), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("forcer=true");
        verify(dossierJuristeRepository, never()).save(any());
    }

    @Test
    void statutEtape_etapeDUnAutreDossier() {
        Dossier autre = Dossier.builder().id(6L).build();
        when(dossierRepository.findById(5L)).thenReturn(Optional.of(dossierSensible(StatutValidation.VALIDEE)));
        when(etapeProcedureRepository.findById(77L)).thenReturn(Optional.of(EtapeProcedure.builder().id(77L)
                .dossier(autre).type(TypeEtape.INSTANCE).statutCycleVie(StatutCycleVie.EN_COURS).build()));

        assertThatThrownBy(() -> service.modifierStatutEtape(5L, 77L, new ModifierStatutEtapeRequest(StatutCycleVie.EN_DELIBERE)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Étape introuvable pour ce dossier : 77");
    }

    @Test
    void derogation_seTrancheParLaValidationTantQueLaDiligenceEstEnAttente() {
        when(dossierRepository.findById(5L)).thenReturn(Optional.of(dossierSensible(StatutValidation.EN_ATTENTE)));

        assertThatThrownBy(() -> service.validerDerogationSeuil(5L, 1L, new ValiderDerogationRequest(StatutValidation.VALIDEE, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pas comme une dérogation");
    }

    @Test
    void derogation_introuvable_dejaTraitee_ouRejeteeSansMotif() {
        Dossier dossier = dossierSensible(StatutValidation.VALIDEE);
        when(dossierRepository.findById(5L)).thenReturn(Optional.of(dossier));
        AuditSeuilDossier traitee = AuditSeuilDossier.builder().id(2L).dossier(dossier).statut(StatutValidation.VALIDEE).build();
        AuditSeuilDossier enAttente = AuditSeuilDossier.builder().id(3L).dossier(dossier).statut(StatutValidation.EN_ATTENTE).build();
        AuditSeuilDossier autreDossier = AuditSeuilDossier.builder().id(4L).dossier(Dossier.builder().id(6L).build())
                .statut(StatutValidation.EN_ATTENTE).build();
        when(auditSeuilDossierRepository.findById(2L)).thenReturn(Optional.of(traitee));
        when(auditSeuilDossierRepository.findById(3L)).thenReturn(Optional.of(enAttente));
        when(auditSeuilDossierRepository.findById(4L)).thenReturn(Optional.of(autreDossier));
        ValiderDerogationRequest validation = new ValiderDerogationRequest(StatutValidation.VALIDEE, null);
        ValiderDerogationRequest rejetSansMotif = new ValiderDerogationRequest(StatutValidation.REJETEE, " ");

        assertThatThrownBy(() -> service.validerDerogationSeuil(5L, 4L, validation))
                .hasMessageContaining("Demande de dérogation introuvable : 4");
        assertThatThrownBy(() -> service.validerDerogationSeuil(5L, 2L, validation))
                .hasMessageContaining("déjà été traitée");
        assertThatThrownBy(() -> service.validerDerogationSeuil(5L, 3L, rejetSansMotif))
                .hasMessageContaining("Le motif est obligatoire en cas de rejet");
        verify(auditSeuilDossierRepository, never()).save(any());
    }

    @Test
    void validationDiligence_sansPropositionEnAttente_ouRejetSansMotif() {
        when(dossierRepository.findById(5L))
                .thenReturn(Optional.of(dossierSensible(StatutValidation.VALIDEE)))
                .thenReturn(Optional.of(dossierSensible(StatutValidation.EN_ATTENTE)));
        ValiderSensibiliteRequest validation = new ValiderSensibiliteRequest(StatutValidation.VALIDEE, null, null, null);
        ValiderSensibiliteRequest rejetSansMotif = new ValiderSensibiliteRequest(StatutValidation.REJETEE, "", null, null);

        assertThatThrownBy(() -> service.validerSensibilite(5L, validation))
                .hasMessageContaining("Aucune proposition de diligence particulière en attente");
        assertThatThrownBy(() -> service.validerSensibilite(5L, rejetSansMotif))
                .hasMessageContaining("Le motif est obligatoire en cas de rejet");
        verify(dossierRepository, never()).save(any());
    }

    @Test
    void miseEnDiligence_exigeSeuilEtTypeConnus_surUnDossierPasDejaSuivi() {
        when(dossierRepository.findById(5L)).thenReturn(Optional.of(dossierSensible(StatutValidation.VALIDEE)));
        when(typeClientSensibleRepository.findByCodeIgnoreCase("INCONNU")).thenReturn(Optional.empty());
        when(typeClientSensibleRepository.findByCodeIgnoreCase("VIP")).thenReturn(Optional.of(new TypeClientSensible()));

        assertThatThrownBy(() -> service.modifierSensibilite(5L, new ModifierSensibiliteRequest(true, null, "VIP", null)))
                .hasMessageContaining("seuilMontant et typeClientSensible sont obligatoires");
        assertThatThrownBy(() -> service.modifierSensibilite(5L, new ModifierSensibiliteRequest(true, 1_000d, "INCONNU", null)))
                .hasMessageContaining("Type de client inconnu du référentiel : INCONNU");
        assertThatThrownBy(() -> service.modifierSensibilite(5L, new ModifierSensibiliteRequest(true, 1_000d, "VIP", null)))
                .hasMessageContaining("déjà suivi en diligence particulière");
        verify(dossierRepository, never()).save(any());
    }
}
