package com.afriland.audiences.dossier.service;

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
import com.afriland.audiences.dossier.web.dto.PartiesDto;
import com.afriland.audiences.dossier.web.dto.ModifierStatutEtapeRequest;
import com.afriland.audiences.dossier.web.dto.ValiderSensibiliteRequest;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.domain.NatureDossier;
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

    /** Bonanjo : une agence reelle du referentiel (V42), pour ne pas inventer de code. */
    private static final String AGENCE = "00002";

    /** Les parties sont obligatoires depuis le 2026-10-05 (observation 6). */
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

        // Le système attribue la référence : les tests n'en fournissent plus aucune. `lenient` car
        // la majorité des tests de cette classe ne créent pas de dossier et ne l'appelleront jamais.
        lenient().when(generateurReference.prochaine(FamilleReference.DOSSIER)).thenReturn("DOS-2026-0001");

        // L'agence d'origine est obligatoire depuis le 2026-10-05 (observation 2) et son code est
        // verifie au referentiel. `lenient` pour la meme raison que la reference : la plupart des
        // tests de cette classe ne creent aucun dossier.
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
     * Depuis le 2026-09-29, la référence est attribuée par le système : un doublon ne peut plus
     * venir d'une saisie. S'il survient, c'est que le compteur (V36) est désynchronisé — le message
     * doit le dire, et ne nommer <b>aucun champ</b> puisqu'il n'y en a plus à corriger à l'écran.
     */
    @Test
    void creerDossier_signaleUneReferenceAttribueeDejaPortee() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase(any()))
                .thenReturn(Optional.of(NatureDossier.builder().categorie(CategorieDossier.RECOUVREMENT).build()));
        when(dossierRepository.existsByReference("DOS-2026-0001")).thenReturn(true);

        assertThatThrownBy(() -> service.creerDossier(baseRequest(false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("désynchronisé")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ApiException.class))
                .extracting(ApiException::getChamp)
                .isNull();
    }

    /** La référence ne vient pas de la requête : elle est demandée au générateur. */
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

        assertThatThrownBy(() -> service.creerDossier(baseRequest(false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Client introuvable");
    }

    @Test
    void creerDossier_rejectsUnknownNature() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(natureDossierRepository.findByLibelleIgnoreCase("Saisie immobilière")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creerDossier(baseRequest(false)))
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
     * Amendement de Q-11 (métier, 2026-09-29) : « ce sont les pièces jointes qui sont obligatoires,
     * pas leur texte associé ». Un dossier de recouvrement se crée donc sans ses références.
     *
     * <p>Ce test tenait la règle inverse jusqu'ici. L'obligation n'a pas disparu, elle a changé
     * d'objet : elle porte désormais sur les fichiers, et ne peut être tenue que par l'écran —
     * une pièce se dépose après la création, qui seule produit l'identifiant du dossier.
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
     * Le code d'agence ne s'invente plus (métier, 2026-10-03, observation 2).
     *
     * <p>Sur les 341 dossiers de recette, un seul code était renseigné - {@code 5466546546} - et il
     * ne désignait aucune agence. Le refus doit nommer le champ fautif : le formulaire porte deux
     * codes d'agence, et l'utilisateur ne saurait pas lequel reprendre.
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

        // Le refus tombe avant toute écriture : aucune référence consommée, aucun dossier enregistré.
        verify(generateurReference, never()).prochaine(any());
        verify(dossierRepository, never()).save(any());
    }

    /**
     * L'agence du contentieux, quand elle est renseignée, passe le même contrôle.
     *
     * <p>Elle reste facultative - un dossier n'est pas toujours transféré - mais un code présent et
     * faux vaut un code inventé : les rapports par agence le compteraient nulle part.
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

        // Q-06 laisse la colonne libre de toute clé étrangère, mais confie ce contrôle au service :
        // sans lui, une faute de frappe était enregistrée en silence et ressortait dans les rapports.
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
        // Sensibilité encore en attente : le seul audit en attente est la proposition initiale.
        Dossier dossier = Dossier.builder().id(1L).estSensible(true)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.validerDerogationSeuil(1L, 5L,
                new ValiderDerogationRequest(StatutValidation.VALIDEE, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("validation de la diligence particulière");
        verify(auditSeuilDossierRepository, never()).save(any());
    }

    @Test
    void listerDerogationsSeuil_filtreSurLeStatutDemande() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(Dossier.builder().id(1L).build()));
        when(auditSeuilDossierRepository.findByDossierIdAndStatutOrderByDateActionDesc(1L, StatutValidation.EN_ATTENTE))
                // Un audit est toujours rattaché à un dossier et à son auteur : le mapper s'y fie.
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

        assertThatThrownBy(() -> service.demanderDerogationSeuil(1L,
                new DemandeDerogationSeuilRequest(80_000_000d, "Motif")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("diligence particulière déjà validé");
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
                    action.getCode(), action.getAction(), action.getDetails(), action.getDateAction());
        }
    }
}
