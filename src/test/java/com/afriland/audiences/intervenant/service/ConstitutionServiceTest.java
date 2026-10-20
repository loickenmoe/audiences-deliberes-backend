package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.intervenant.domain.LettreConstitution;
import com.afriland.audiences.intervenant.repository.LettreConstitutionRepository;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.mapper.DemandeConstitutionMapper;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.intervenant.web.dto.CreerConstitutionRequest;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import com.afriland.audiences.intervenant.web.dto.ValiderConstitutionRequest;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.AutrePrestataire;
import com.afriland.audiences.referentiel.domain.Intervenant;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import com.afriland.audiences.referentiel.service.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConstitutionServiceTest {

    @Mock private DemandeConstitutionRepository demandeConstitutionRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private IntervenantRepository intervenantRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private AlerteService alerteService;
    @Mock private LettreConstitutionService lettreConstitutionService;
    @Mock private SignatureService signatureService;
    @Mock private LettreConstitutionRepository lettreConstitutionRepository;
    @Mock private MinioStorageService minioStorageService;
    @Mock private DocumentRepository documentRepository;
    @Mock private AvocatRepository avocatRepository;

    private final DemandeConstitutionMapper mapper = demande -> new DemandeConstitutionResponse(
            demande.getId(), demande.getDossier() != null ? demande.getDossier().getId() : null,
            demande.getPrestataire() != null ? demande.getPrestataire().getId() : null,
            demande.getDemandeur() != null ? demande.getDemandeur().getId() : null, demande.getMotif(),
            demande.getStatut(), demande.getValidePar() != null ? demande.getValidePar().getId() : null,
            demande.getMotifRejet(), demande.getLettreDocument() != null ? demande.getLettreDocument().getId() : null,
            demande.getDateDemande(), demande.getDateDecision(),
            demande.getDossier() != null ? demande.getDossier().getReference() : null,
            demande.getPrestataire() != null ? demande.getPrestataire().getNom() : null, null, null, null,
            // The mapper does not know the session: the service sets the write permission after it.
            false);

    private ConstitutionService service;
    private Dossier dossier;
    private Utilisateur juriste;
    private Utilisateur sh;

    @Mock
    private NotificationPrestataireService notificationPrestataireService;

    @BeforeEach
    void setUp() {
        service = new ConstitutionService(demandeConstitutionRepository, dossierRepository, intervenantRepository,
                utilisateurRepository, currentUserService, historiqueActionService, alerteService,
                lettreConstitutionService, signatureService, lettreConstitutionRepository, minioStorageService,
                documentRepository, mapper,
                new CompteAvocatService(avocatRepository, utilisateurRepository), notificationPrestataireService);

        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        juriste = Utilisateur.builder().id(10L).nom("Juriste").prenom("Test").build();
        sh = Utilisateur.builder().id(30L).nom("Sup").prenom("Hierarchique").build();
    }

    @Test
    void creerDemande_nominalNotifiesSh() {
        Intervenant prestataire = Avocat.builder().id(5L).nom("Me Fotso").compteKeycloak("kc-5").build();
        Utilisateur shActif = Utilisateur.builder().id(31L).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(intervenantRepository.findById(5L)).thenReturn(Optional.of(prestataire));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(demandeConstitutionRepository.save(any(DemandeConstitution.class))).thenAnswer(inv -> {
            DemandeConstitution d = inv.getArgument(0);
            d.setId(100L);
            return d;
        });
        when(utilisateurRepository.findByProfilCodeAndActifTrue("SH")).thenReturn(List.of(shActif));

        DemandeConstitutionResponse result = service.creerDemande(new CreerConstitutionRequest(1L, 5L, "Besoin d'un avocat local"));

        assertThat(result.statut()).isEqualTo(StatutConstitution.EN_ATTENTE);
        verify(alerteService).declencher(eq(TypeAlerte.CONSTITUTION_A_SIGNER), eq(1L), eq(shActif), any());
    }

    @Test
    void valider_rejectsAlreadyDecided() {
        DemandeConstitution demande = DemandeConstitution.builder().id(100L).dossier(dossier)
                .statut(StatutConstitution.VALIDEE).build();
        when(demandeConstitutionRepository.findById(100L)).thenReturn(Optional.of(demande));

        var requete = new ValiderConstitutionRequest(StatutConstitution.VALIDEE, null);
        assertThatThrownBy(() -> service.valider(100L, requete))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void valider_rejectionRequiresMotif() {
        DemandeConstitution demande = DemandeConstitution.builder().id(100L).dossier(dossier)
                .statut(StatutConstitution.EN_ATTENTE).demandeur(juriste).build();
        when(demandeConstitutionRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(sh);

        var requete = new ValiderConstitutionRequest(StatutConstitution.REJETEE, null);
        assertThatThrownBy(() -> service.valider(100L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("motif");
    }

    @Test
    void valider_rejectionNotifiesDemandeur() {
        Intervenant prestataire = Avocat.builder().id(5L).nom("Me Fotso").build();
        DemandeConstitution demande = DemandeConstitution.builder().id(100L).dossier(dossier).prestataire(prestataire)
                .statut(StatutConstitution.EN_ATTENTE).demandeur(juriste).build();
        when(demandeConstitutionRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(sh);
        when(demandeConstitutionRepository.save(any(DemandeConstitution.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandeConstitutionResponse result = service.valider(100L, new ValiderConstitutionRequest(StatutConstitution.REJETEE, "Pas de besoin identifié"));

        assertThat(result.statut()).isEqualTo(StatutConstitution.REJETEE);
        assertThat(result.motifRejet()).isEqualTo("Pas de besoin identifié");
        verify(alerteService).declencher(eq(TypeAlerte.CONSTITUTION_A_SIGNER), eq(1L), eq(juriste), any());
        verify(lettreConstitutionService, never()).generer(any(LettreConstitution.class), any(), any());
    }

    @Test
    void valider_approvalGeneratesLettreAndNotifiesAvocat() {
        Avocat avocat = Avocat.builder().id(5L).nom("Me Fotso").compteKeycloak("kc-5").build();
        Utilisateur utilisateurAvocat = Utilisateur.builder().id(50L).build();
        DemandeConstitution demande = DemandeConstitution.builder().id(100L).dossier(dossier).prestataire(avocat)
                .statut(StatutConstitution.EN_ATTENTE).demandeur(juriste).motif("Besoin local").build();
        when(demandeConstitutionRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(sh);
        /*
         * The letter is a stored document: the service composes a draft, upload gives it its number, then
         * rendering lays it out. The draft is created **without a number** (B2): the number is only drawn
         * at signature, so none is burned on an abandoned request (the series never restarts at one).
         */
        when(lettreConstitutionRepository.findByDemandeId(any())).thenReturn(Optional.empty());
        when(lettreConstitutionRepository.prochainNumero()).thenReturn(4L);
        when(lettreConstitutionService.brouillonPour(any(), eq(null)))
                .thenReturn(LettreConstitution.builder().suffixeNumero("First Bank/DAJR").build());
        when(lettreConstitutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lettreConstitutionService.generer(any(LettreConstitution.class), any(), any()))
                .thenReturn(new byte[]{1, 2, 3});
        when(minioStorageService.deposer(eq(1L), any(), eq("application/pdf"), any())).thenReturn("dossiers/1/lettre.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(200L);
            return d;
        });
        when(demandeConstitutionRepository.save(any(DemandeConstitution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurRepository.findByKeycloakId("kc-5")).thenReturn(Optional.of(utilisateurAvocat));

        DemandeConstitutionResponse result = service.valider(100L, new ValiderConstitutionRequest(StatutConstitution.VALIDEE, null));

        assertThat(result.statut()).isEqualTo(StatutConstitution.VALIDEE);
        assertThat(result.lettreDocumentId()).isEqualTo(200L);
        verify(alerteService).declencher(eq(TypeAlerte.CONSTITUTION_A_SIGNER), eq(1L), eq(utilisateurAvocat), any());
    }

    /**
     * The letter carries <b>two</b> signatures, each taken from its holder's account, never from the
     * free-text "signataire" field (that would allow signing on someone else's behalf).
     */
    @Test
    void valider_apposeLesSignaturesDuRedacteurEtDuValideur() {
        Avocat avocat = Avocat.builder().id(5L).nom("Me Fotso").build();
        DemandeConstitution demande = DemandeConstitution.builder().id(100L).dossier(dossier).prestataire(avocat)
                .statut(StatutConstitution.EN_ATTENTE).demandeur(juriste).motif("Besoin local").build();
        LettreConstitution lettre = LettreConstitution.builder()
                .suffixeNumero("First Bank/DAJR").demande(demande).redigePar(juriste).build();
        preparerSignature(demande, lettre);
        when(signatureService.pour(10L)).thenReturn(Optional.of(
                new SignatureService.SignatureDeposee(new byte[]{9}, "image/png", null)));
        when(signatureService.pour(30L)).thenReturn(Optional.of(
                new SignatureService.SignatureDeposee(new byte[]{8}, "image/png", null)));

        service.valider(100L, new ValiderConstitutionRequest(StatutConstitution.VALIDEE, null));

        ArgumentCaptor<LettreConstitutionService.Signataire> gauche =
                ArgumentCaptor.forClass(LettreConstitutionService.Signataire.class);
        ArgumentCaptor<LettreConstitutionService.Signataire> droite =
                ArgumentCaptor.forClass(LettreConstitutionService.Signataire.class);
        verify(lettreConstitutionService).generer(any(LettreConstitution.class), gauche.capture(), droite.capture());

        assertThat(gauche.getValue().nom()).isEqualTo("Test Juriste");
        assertThat(gauche.getValue().image()).containsExactly(9);
        assertThat(droite.getValue().nom()).isEqualTo("Hierarchique Sup");
        assertThat(droite.getValue().image()).containsExactly(8);
    }

    /**
     * The SH who corrected the letter does not sign <b>twice</b>. Having rewritten it, they become the
     * last author; the legal officer who requested the retainer is used instead. The correction stays
     * traced on screen and in the history.
     */
    @Test
    void valider_quandLeShACorrigeLaLettre_laGaucheRevientAuJuristeQuiASollicite() {
        Avocat avocat = Avocat.builder().id(5L).nom("Me Fotso").build();
        DemandeConstitution demande = DemandeConstitution.builder().id(100L).dossier(dossier).prestataire(avocat)
                .statut(StatutConstitution.EN_ATTENTE).demandeur(juriste).motif("Besoin local").build();
        LettreConstitution lettre = LettreConstitution.builder()
                .suffixeNumero("First Bank/DAJR").demande(demande).redigePar(sh).build();
        preparerSignature(demande, lettre);

        service.valider(100L, new ValiderConstitutionRequest(StatutConstitution.VALIDEE, null));

        ArgumentCaptor<LettreConstitutionService.Signataire> gauche =
                ArgumentCaptor.forClass(LettreConstitutionService.Signataire.class);
        ArgumentCaptor<LettreConstitutionService.Signataire> droite =
                ArgumentCaptor.forClass(LettreConstitutionService.Signataire.class);
        verify(lettreConstitutionService).generer(any(LettreConstitution.class), gauche.capture(), droite.capture());

        assertThat(gauche.getValue().nom()).isEqualTo("Test Juriste");
        assertThat(droite.getValue().nom()).isEqualTo("Hierarchique Sup");
    }

    /** Shared setup for both signature journeys: request, letter, GED. */
    private void preparerSignature(DemandeConstitution demande, LettreConstitution lettre) {
        when(demandeConstitutionRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(sh);
        when(lettreConstitutionRepository.findByDemandeId(any())).thenReturn(Optional.of(lettre));
        when(lettreConstitutionRepository.prochainNumero()).thenReturn(4L);
        when(lettreConstitutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lettreConstitutionService.generer(any(LettreConstitution.class), any(), any()))
                .thenReturn(new byte[]{1, 2, 3});
        when(minioStorageService.deposer(eq(1L), any(), eq("application/pdf"), any()))
                .thenReturn("dossiers/1/lettre.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(200L);
            return d;
        });
        when(demandeConstitutionRepository.save(any(DemandeConstitution.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void valider_approvalSkipsNotificationForAutrePrestataireWithoutAccount() {
        AutrePrestataire prestataire = AutrePrestataire.builder().id(6L).nom("Notaire Kamga").build();
        DemandeConstitution demande = DemandeConstitution.builder().id(100L).dossier(dossier).prestataire(prestataire)
                .statut(StatutConstitution.EN_ATTENTE).demandeur(juriste).motif("Besoin local").build();
        when(demandeConstitutionRepository.findById(100L)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(sh);
        /*
         * The letter is a stored document: the service composes a draft, upload gives it its number, then
         * rendering lays it out. The draft is created **without a number** (B2): the number is only drawn
         * at signature, so none is burned on an abandoned request (the series never restarts at one).
         */
        when(lettreConstitutionRepository.findByDemandeId(any())).thenReturn(Optional.empty());
        when(lettreConstitutionRepository.prochainNumero()).thenReturn(4L);
        when(lettreConstitutionService.brouillonPour(any(), eq(null)))
                .thenReturn(LettreConstitution.builder().suffixeNumero("First Bank/DAJR").build());
        when(lettreConstitutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lettreConstitutionService.generer(any(LettreConstitution.class), any(), any()))
                .thenReturn(new byte[]{1, 2, 3});
        when(minioStorageService.deposer(eq(1L), any(), eq("application/pdf"), any())).thenReturn("dossiers/1/lettre.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(200L);
            return d;
        });
        when(demandeConstitutionRepository.save(any(DemandeConstitution.class))).thenAnswer(inv -> inv.getArgument(0));

        service.valider(100L, new ValiderConstitutionRequest(StatutConstitution.VALIDEE, null));

        verify(alerteService, never()).declencher(any(), any(), any(), any());
        verify(utilisateurRepository, never()).findByKeycloakId(any());
    }

    @Test
    void lister_filtreParStatut_alimenteLaFileDuSh() {
        Intervenant prestataire = AutrePrestataire.builder().id(5L).nom("Notaire").build();
        when(demandeConstitutionRepository.findByStatutOrderByDateDemandeAsc(eq(StatutConstitution.EN_ATTENTE), any()))
                .thenReturn(new PageImpl<>(List.of(DemandeConstitution.builder().id(100L).dossier(dossier)
                        .prestataire(prestataire).demandeur(juriste).motif("Acte notarié")
                        .statut(StatutConstitution.EN_ATTENTE).build())));

        var result = service.lister(StatutConstitution.EN_ATTENTE, PageRequest.of(0, 10)).content();

        assertThat(result).singleElement()
                .satisfies(d -> assertThat(d.statut()).isEqualTo(StatutConstitution.EN_ATTENTE));
        verify(demandeConstitutionRepository, never()).findByOrderByDateDemandeDesc(any());
    }

    @Test
    void lister_sansFiltre_retourneToutLHistorique() {
        Intervenant prestataire = AutrePrestataire.builder().id(5L).nom("Notaire").build();
        when(demandeConstitutionRepository.findByOrderByDateDemandeDesc(any())).thenReturn(new PageImpl<>(List.of(
                DemandeConstitution.builder().id(101L).dossier(dossier).prestataire(prestataire)
                        .demandeur(juriste).statut(StatutConstitution.VALIDEE).validePar(sh).build(),
                DemandeConstitution.builder().id(100L).dossier(dossier).prestataire(prestataire)
                        .demandeur(juriste).statut(StatutConstitution.REJETEE).validePar(sh)
                        .motifRejet("Hors budget").build())));

        assertThat(service.lister(null, PageRequest.of(0, 10)).content())
                .extracting(DemandeConstitutionResponse::id).containsExactly(101L, 100L);
    }
    /**
     * Printing on pre-printed paper starts from the <b>GED document</b>: the business reprints the letter
     * from the case's Documents tab, which only knows the document, never the retainer request.
     */
    @Test
    void lettrePourPapierPreimprime_regenereDepuisLaLettreEnregistree() {
        Document piece = Document.builder().id(77L).nomFichier("lettre-constitution-4.pdf").build();
        DemandeConstitution demande = DemandeConstitution.builder().id(100L).lettreDocument(piece).build();
        LettreConstitution lettre = LettreConstitution.builder().id(9L).numero(4).demande(demande).build();
        when(demandeConstitutionRepository.findByLettreDocumentId(77L)).thenReturn(Optional.of(demande));
        when(lettreConstitutionRepository.findByDemandeId(100L)).thenReturn(Optional.of(lettre));
        // The pre-printed copy carries the same signatures as the archived one (N8): here the request has
        // neither author nor validator, so the letter goes out with names only.
        when(lettreConstitutionService.generer(eq(lettre), eq(true), isNull(), isNull()))
                .thenReturn("%PDF-preimprime".getBytes(StandardCharsets.UTF_8));

        byte[] pdf = service.lettrePourPapierPreimprime(77L);

        assertThat(new String(pdf, StandardCharsets.UTF_8)).isEqualTo("%PDF-preimprime");
        // The copy is regenerated: nothing is uploaded to the GED, no second copy is created.
        verify(minioStorageService, never()).deposer(any(), any(), any(), any());
    }

    /** The GED holds all kinds of documents: no other one can be regenerated. */
    @Test
    void lettrePourPapierPreimprime_refuseUnDocumentQuiNEstPasUneLettre() {
        when(demandeConstitutionRepository.findByLettreDocumentId(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lettrePourPapierPreimprime(55L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pas une lettre de constitution");
    }

}
