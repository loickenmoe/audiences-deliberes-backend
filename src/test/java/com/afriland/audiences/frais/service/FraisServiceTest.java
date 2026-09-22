package com.afriland.audiences.frais.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.SeuilOrigine;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.frais.domain.DecisionValidationFrais;
import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.EtapeValidationFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.domain.StatutPaiementFrais;
import com.afriland.audiences.frais.domain.ValidationFrais;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.frais.repository.ValidationFraisRepository;
import com.afriland.audiences.frais.web.dto.ControleConformiteRequest;
import com.afriland.audiences.frais.web.dto.ControleOpportuniteRequest;
import com.afriland.audiences.frais.web.dto.DemandeFraisResponse;
import com.afriland.audiences.frais.web.dto.DeposerFraisRequest;
import com.afriland.audiences.frais.web.dto.ValidationConjointeRequest;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.referentiel.service.ConfigurationService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraisServiceTest {

    @Mock private DemandeFraisRepository demandeFraisRepository;
    @Mock private ValidationFraisRepository validationFraisRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private DossierAvocatRepository dossierAvocatRepository;
    @Mock private AvocatRepository avocatRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private AlerteService alerteService;
    @Mock private ConfigurationService configurationService;
    // Q-97 : URL pré-signées des pièces justificatives, sur le détail seulement.
    @Mock private MinioStorageService minioStorageService;

    private FraisService service;
    private Dossier dossier;
    private Avocat avocat;
    private Utilisateur utilisateurAvocat;
    private Utilisateur assistante;
    private Utilisateur dja;
    private Utilisateur dj;

    @BeforeEach
    void setUp() {
        service = new FraisService(demandeFraisRepository, validationFraisRepository, dossierRepository,
                dossierAvocatRepository, new CompteAvocatService(avocatRepository, utilisateurRepository),
                utilisateurRepository, currentUserService,
                historiqueActionService, alerteService, configurationService, minioStorageService);
        // Toute réponse porte le seuil applicable (Q-82).
        lenient().when(configurationService.getOrThrow("SEUIL_MONTANT_DEFAUT"))
                .thenReturn(ConfigurationSysteme.builder().cle("SEUIL_MONTANT_DEFAUT").valeur("50000000").build());

        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").estSensible(false)
                .seuilOrigine(SeuilOrigine.GLOBAL).build();
        avocat = Avocat.builder().id(5L).nom("Me Fotso").compteKeycloak("kc-avocat").build();
        utilisateurAvocat = Utilisateur.builder().id(50L).keycloakId("kc-avocat")
                .profil(Profil.builder().code("AVOCAT").build()).build();
        assistante = Utilisateur.builder().id(20L).profil(Profil.builder().code("ASSISTANTE").build()).build();
        dja = Utilisateur.builder().id(30L).profil(Profil.builder().code("DJA").build()).build();
        dj = Utilisateur.builder().id(40L).profil(Profil.builder().code("DJ").build()).build();
    }

    // --- Dépôt (FR-INT-01) ---

    @Test
    void deposer_rejectsAvocatNotAffected() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));
        when(dossierAvocatRepository.existsByDossierIdAndAvocatId(1L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> service.deposer(new DeposerFraisRequest(1L, 1_000_000d, List.of("piece.pdf"), "FAC-001")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void deposer_rejectsDuplicateFacture() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));
        when(dossierAvocatRepository.existsByDossierIdAndAvocatId(1L, 5L)).thenReturn(true);
        when(demandeFraisRepository.existsByReferenceFacture("FAC-001")).thenReturn(true);

        assertThatThrownBy(() -> service.deposer(new DeposerFraisRequest(1L, 1_000_000d, List.of("piece.pdf"), "FAC-001")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("FAC-001");
    }

    @Test
    void deposer_nominalNotifiesAssistante() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));
        when(dossierAvocatRepository.existsByDossierIdAndAvocatId(1L, 5L)).thenReturn(true);
        when(demandeFraisRepository.existsByReferenceFacture("FAC-001")).thenReturn(false);
        when(demandeFraisRepository.save(any(DemandeFrais.class))).thenAnswer(inv -> {
            DemandeFrais d = inv.getArgument(0);
            d.setId(100L);
            return d;
        });
        when(utilisateurRepository.findByProfilCodeAndActifTrue("ASSISTANTE")).thenReturn(List.of(assistante));

        DemandeFraisResponse result = service.deposer(new DeposerFraisRequest(1L, 1_000_000d, List.of("piece.pdf"), "FAC-001"));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.DEPOSEE);
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_DEMANDE_FRAIS), eq(1L), eq(assistante), any());
    }

    // --- Conformité (FR-INT-02) ---

    @Test
    void controlerConformite_rejectsWrongStatut() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.CONFORMITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.controlerConformite(100L, new ControleConformiteRequest(true, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void controlerConformite_conformeNotifiesDja() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.DEPOSEE).montant(1_000_000d).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(assistante);
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of(dja));

        DemandeFraisResponse result = service.controlerConformite(100L, new ControleConformiteRequest(true, null));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.CONFORMITE);
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_DEMANDE_FRAIS), eq(1L), eq(dja), any());
    }

    @Test
    void controlerConformite_rejetRequiresMotif() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.DEPOSEE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(assistante);

        assertThatThrownBy(() -> service.controlerConformite(100L, new ControleConformiteRequest(false, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("motif");
    }

    @Test
    void controlerConformite_rejetNotifiesAvocat() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.DEPOSEE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(assistante);
        when(utilisateurRepository.findByKeycloakId("kc-avocat")).thenReturn(Optional.of(utilisateurAvocat));

        DemandeFraisResponse result = service.controlerConformite(100L, new ControleConformiteRequest(false, "Pièces incomplètes"));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.REJETEE);
        assertThat(result.motifRejet()).isEqualTo("Pièces incomplètes");
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_DEMANDE_FRAIS), eq(1L), eq(utilisateurAvocat), any());
    }

    // --- Opportunité (FR-INT-03) ---

    @Test
    void controlerOpportunite_nonSensibleUnderSeuilValidatesDirectly() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat).montant(1_000_000d)
                .statutCircuit(StatutCircuitFrais.CONFORMITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dja);
        lenient().when(configurationService.getOrThrow("SEUIL_MONTANT_DEFAUT"))
                .thenReturn(ConfigurationSysteme.builder().cle("SEUIL_MONTANT_DEFAUT").valeur("50000000").build());

        DemandeFraisResponse result = service.controlerOpportunite(100L, new ControleOpportuniteRequest(true, null));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.VALIDEE);
        verify(alerteService, never()).declencher(eq(TypeAlerte.VALIDATION_CONJOINTE_REQUISE), any(), any(), any());
    }

    @Test
    void controlerOpportunite_sensibleTriggersValidationConjointe() {
        Dossier sensible = Dossier.builder().id(1L).reference("DOS-2026-002").estSensible(true)
                .seuilOrigine(SeuilOrigine.GLOBAL).build();
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(sensible).avocat(avocat).montant(1_000_000d)
                .statutCircuit(StatutCircuitFrais.CONFORMITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dja);
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(dj));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of(dja));

        DemandeFraisResponse result = service.controlerOpportunite(100L, new ControleOpportuniteRequest(true, null));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.OPPORTUNITE);
        verify(alerteService).declencher(eq(TypeAlerte.VALIDATION_CONJOINTE_REQUISE), eq(1L), eq(dj), any());
        verify(alerteService).declencher(eq(TypeAlerte.VALIDATION_CONJOINTE_REQUISE), eq(1L), eq(dja), any());
    }

    @Test
    void controlerOpportunite_montantOverSeuilTriggersValidationConjointe() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat).montant(60_000_000d)
                .statutCircuit(StatutCircuitFrais.CONFORMITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dja);
        when(configurationService.getOrThrow("SEUIL_MONTANT_DEFAUT"))
                .thenReturn(ConfigurationSysteme.builder().cle("SEUIL_MONTANT_DEFAUT").valeur("50000000").build());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(dj));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of(dja));

        DemandeFraisResponse result = service.controlerOpportunite(100L, new ControleOpportuniteRequest(true, null));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.OPPORTUNITE);
    }

    @Test
    void controlerOpportunite_rejetNotifiesAvocat() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat).montant(1_000_000d)
                .statutCircuit(StatutCircuitFrais.CONFORMITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dja);
        when(utilisateurRepository.findByKeycloakId("kc-avocat")).thenReturn(Optional.of(utilisateurAvocat));

        DemandeFraisResponse result = service.controlerOpportunite(100L, new ControleOpportuniteRequest(false, "Dépense injustifiée"));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.REJETEE);
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_DEMANDE_FRAIS), eq(1L), eq(utilisateurAvocat), any());
    }

    // --- Validation conjointe (FR-INT-04) ---

    @Test
    void validerConjointement_singleAccordDoesNotValidate() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.OPPORTUNITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(validationFraisRepository.findByDemandeFraisIdAndEtape(100L, EtapeValidationFrais.CONJOINTE_DJ))
                .thenReturn(Optional.empty());
        when(validationFraisRepository.existsByDemandeFraisIdAndEtapeAndDecision(
                100L, EtapeValidationFrais.CONJOINTE_DJA, DecisionValidationFrais.ACCORD)).thenReturn(false);

        DemandeFraisResponse result = service.validerConjointement(100L, new ValidationConjointeRequest(true, null));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.OPPORTUNITE);
        verify(demandeFraisRepository, never()).save(any());
    }

    @Test
    void validerConjointement_bothAccordsValidates() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.OPPORTUNITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dja);
        when(validationFraisRepository.findByDemandeFraisIdAndEtape(100L, EtapeValidationFrais.CONJOINTE_DJA))
                .thenReturn(Optional.empty());
        when(validationFraisRepository.existsByDemandeFraisIdAndEtapeAndDecision(
                100L, EtapeValidationFrais.CONJOINTE_DJ, DecisionValidationFrais.ACCORD)).thenReturn(true);
        when(demandeFraisRepository.save(any(DemandeFrais.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandeFraisResponse result = service.validerConjointement(100L, new ValidationConjointeRequest(true, null));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.VALIDEE);
    }

    @Test
    void validerConjointement_oneRefusalRejects() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.OPPORTUNITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(validationFraisRepository.findByDemandeFraisIdAndEtape(100L, EtapeValidationFrais.CONJOINTE_DJ))
                .thenReturn(Optional.empty());
        when(utilisateurRepository.findByKeycloakId("kc-avocat")).thenReturn(Optional.of(utilisateurAvocat));

        DemandeFraisResponse result = service.validerConjointement(100L, new ValidationConjointeRequest(false, "Montant excessif"));

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.REJETEE);
    }

    @Test
    void validerConjointement_rejectsDuplicateVote() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.OPPORTUNITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(validationFraisRepository.findByDemandeFraisIdAndEtape(100L, EtapeValidationFrais.CONJOINTE_DJ))
                .thenReturn(Optional.of(com.afriland.audiences.frais.domain.ValidationFrais.builder().id(1L).build()));

        assertThatThrownBy(() -> service.validerConjointement(100L, new ValidationConjointeRequest(true, null)))
                .isInstanceOf(ApiException.class);
    }

    // --- Paiement (FR-INT-05) ---

    @Test
    void enregistrerPaiement_rejectsWhenNotValidee() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.OPPORTUNITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.enregistrerPaiement(100L)).isInstanceOf(ApiException.class);
    }

    @Test
    void enregistrerPaiement_nominalNotifiesAvocatAndDjDja() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.VALIDEE).statutPaiement(StatutPaiementFrais.EN_ATTENTE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(demandeFraisRepository.save(any(DemandeFrais.class))).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurRepository.findByKeycloakId("kc-avocat")).thenReturn(Optional.of(utilisateurAvocat));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(dj));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of(dja));

        DemandeFraisResponse result = service.enregistrerPaiement(100L);

        assertThat(result.statutCircuit()).isEqualTo(StatutCircuitFrais.PAYEE);
        assertThat(result.statutPaiement()).isEqualTo(StatutPaiementFrais.PAYEE);
        verify(alerteService).declencher(eq(TypeAlerte.PAIEMENT_EFFECTUE), eq(1L), eq(utilisateurAvocat), any());
        verify(alerteService).declencher(eq(TypeAlerte.PAIEMENT_EFFECTUE), eq(1L), eq(dj), any());
        verify(alerteService).declencher(eq(TypeAlerte.PAIEMENT_EFFECTUE), eq(1L), eq(dja), any());
    }

    // --- Consultation (Q-82) ---

    @Test
    void consulter_avocatCannotReadAnotherAvocatsDemande() {
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutCircuit(StatutCircuitFrais.DEPOSEE).montant(1_000_000d).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(Utilisateur.builder().id(51L).keycloakId("kc-autre")
                .profil(Profil.builder().code("AVOCAT").build()).build());

        assertThatThrownBy(() -> service.consulter(100L)).isInstanceOf(ApiException.class);
    }

    @Test
    void consulter_exposesDecisionsAndJointValidationRule() {
        Dossier sensible = Dossier.builder().id(1L).reference("DOS-2026-002").estSensible(true)
                .seuilOrigine(SeuilOrigine.GLOBAL).build();
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(sensible).avocat(avocat).montant(1_000_000d)
                .statutCircuit(StatutCircuitFrais.OPPORTUNITE).build();
        Utilisateur djaNommee = Utilisateur.builder().id(30L).prenom("Awa").nom("Mbarga")
                .profil(Profil.builder().code("DJA").build()).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(validationFraisRepository.findByDemandeFraisIdOrderByDateValidationAscIdAsc(100L)).thenReturn(List.of(
                ValidationFrais.builder().etape(EtapeValidationFrais.OPPORTUNITE)
                        .decision(DecisionValidationFrais.ACCORD).utilisateur(djaNommee).build()));

        DemandeFraisResponse result = service.consulter(100L);

        assertThat(result.referenceDossier()).isEqualTo("DOS-2026-002");
        assertThat(result.avocatNom()).isEqualTo("Me Fotso");
        assertThat(result.seuilApplicable()).isEqualTo(50_000_000d);
        assertThat(result.validationConjointeRequise()).isTrue();
        assertThat(result.validations()).singleElement().satisfies(v -> {
            assertThat(v.etape()).isEqualTo(EtapeValidationFrais.OPPORTUNITE);
            assertThat(v.utilisateurNom()).isEqualTo("Awa Mbarga");
        });
    }

    @Test
    void consulter_appliesDerogationThreshold() {
        Dossier derogation = Dossier.builder().id(1L).reference("DOS-2026-003").estSensible(false)
                .seuilOrigine(SeuilOrigine.DEROGATION).seuilMontant(500_000d).build();
        DemandeFrais demande = DemandeFrais.builder().id(100L).dossier(derogation).avocat(avocat).montant(1_000_000d)
                .statutCircuit(StatutCircuitFrais.CONFORMITE).build();
        when(demandeFraisRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dja);

        DemandeFraisResponse result = service.consulter(100L);

        assertThat(result.seuilApplicable()).isEqualTo(500_000d);
        assertThat(result.validationConjointeRequise()).isTrue();
    }
}
