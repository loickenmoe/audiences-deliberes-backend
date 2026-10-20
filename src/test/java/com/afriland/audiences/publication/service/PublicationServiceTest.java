package com.afriland.audiences.publication.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.publication.domain.CommentairePublication;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.ged.service.ValidationFichierService;
import com.afriland.audiences.publication.domain.AutrePublication;
import com.afriland.audiences.publication.domain.CompteRenduAudience;
import com.afriland.audiences.publication.domain.CorrespondanceAvocat;
import com.afriland.audiences.publication.domain.Publication;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.domain.TypeAutrePublication;
import com.afriland.audiences.publication.mapper.PublicationMapper;
import com.afriland.audiences.publication.repository.CommentairePublicationRepository;
import com.afriland.audiences.publication.repository.CorrespondanceAvocatRepository;
import com.afriland.audiences.publication.repository.PublicationRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.publication.web.dto.CommentaireRequest;
import com.afriland.audiences.publication.web.dto.CommentaireResponse;
import com.afriland.audiences.publication.web.dto.CorrespondanceRequest;
import com.afriland.audiences.publication.web.dto.DeposerCompteRenduRequest;
import com.afriland.audiences.publication.web.dto.PublicationResponse;
import com.afriland.audiences.publication.web.dto.ValiderPublicationRequest;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTest {

    @Mock private PublicationRepository publicationRepository;
    @Mock private CommentairePublicationRepository commentairePublicationRepository;
    @Mock private CorrespondanceAvocatRepository correspondanceAvocatRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private AudienceRepository audienceRepository;
    @Mock private AvocatRepository avocatRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private AlerteService alerteService;
    @Mock private MinioStorageService minioStorageService;
    // FORMATS_GED / TAILLE_MAX_GED also govern a report's attachment.
    @Mock private ConfigurationService configurationService;
    @Mock private DossierJuristeRepository dossierJuristeRepository;

    private final PublicationMapper mapper = new PublicationMapper();

    private PublicationService service;
    private Dossier dossier;
    private Avocat avocat;
    private Utilisateur utilisateurAvocat;
    private Utilisateur assistante;
    private Utilisateur juriste;
    private Utilisateur dj;

    @Mock
    private NotificationPrestataireService notificationPrestataireService;

    @BeforeEach
    void setUp() {
        service = new PublicationService(publicationRepository, commentairePublicationRepository,
                correspondanceAvocatRepository, dossierRepository, audienceRepository,
                new CompteAvocatService(avocatRepository, utilisateurRepository), notificationPrestataireService,
                utilisateurRepository, currentUserService, historiqueActionService, alerteService,
                minioStorageService, new ValidationFichierService(configurationService), mapper,
                dossierJuristeRepository);

        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        avocat = Avocat.builder().id(5L).nom("Me Fotso").compteKeycloak("kc-avocat").build();
        utilisateurAvocat = Utilisateur.builder().id(50L).keycloakId("kc-avocat")
                .profil(Profil.builder().code("AVOCAT").build()).build();
        assistante = Utilisateur.builder().id(20L).profil(Profil.builder().code("ASSISTANTE").build()).build();
        juriste = Utilisateur.builder().id(10L).profil(Profil.builder().code("JURISTE").build()).build();
        dj = Utilisateur.builder().id(40L).profil(Profil.builder().code("DJ").build()).build();
    }

    // Report upload (US 5.1)

    @Test
    void deposerCompteRendu_rejectsAudienceNotAttachedToDossier() {
        Dossier autreDossier = Dossier.builder().id(2L).build();
        Audience audience = Audience.builder().id(7L).dossier(autreDossier).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(audienceRepository.findById(7L)).thenReturn(Optional.of(audience));

        var requete = new DeposerCompteRenduRequest(1L, 7L, "Contenu", null);
        assertThatThrownBy(() -> service.deposerCompteRendu(requete, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void deposerCompteRendu_nominalNotifiesAssistante() {
        Audience audience = Audience.builder().id(7L).dossier(dossier).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(audienceRepository.findById(7L)).thenReturn(Optional.of(audience));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> {
            CompteRenduAudience p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(utilisateurRepository.findByProfilCodeAndActifTrue("ASSISTANTE")).thenReturn(List.of(assistante));

        // Without attachment: the nominal case of US 5.1.
        PublicationResponse result =
                service.deposerCompteRendu(new DeposerCompteRenduRequest(1L, 7L, "Contenu du CR", null), null);

        assertThat(result.statutValidation()).isEqualTo(StatutPublication.DEPOSE);
        assertThat(result.audienceId()).isEqualTo(7L);
        assertThat(result.nomFichier()).as("aucune pièce n'a été jointe").isNull();
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(assistante), any());
        verifyNoInteractions(minioStorageService);
    }

    /**
     * The attachment is optional, but when present it goes through the same checks as the GED and lands
     * in the same storage. The narrative stays mandatory.
     */
    @Test
    void deposerCompteRendu_avecPieceJointe_lEnregistreEtLaNomme() {
        Audience audience = Audience.builder().id(7L).dossier(dossier).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(audienceRepository.findById(7L)).thenReturn(Optional.of(audience));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));
        when(configurationService.getOrThrow("FORMATS_GED")).thenReturn(
                ConfigurationSysteme.builder().cle("FORMATS_GED").valeur("PDF,PNG,JPG,TXT,XLSX").build());
        when(configurationService.getOrThrow("TAILLE_MAX_GED")).thenReturn(
                ConfigurationSysteme.builder().cle("TAILLE_MAX_GED").valeur("10485760").build());
        // Real MinioStorageService key: {uuid}-{name}, whose prefix the mapper strips.
        when(minioStorageService.deposer(eq(1L), any()))
                .thenReturn("dossiers/1/3f1c2b8e-9a4d-4c7e-8b1a-5d6e7f809a1b-plumitif.pdf");
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> {
            CompteRenduAudience p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });
        when(utilisateurRepository.findByProfilCodeAndActifTrue("ASSISTANTE")).thenReturn(List.of(assistante));

        PublicationResponse result = service.deposerCompteRendu(
                new DeposerCompteRenduRequest(1L, 7L, "Renvoi au 12", null),
                new MockMultipartFile("file", "plumitif.pdf", "application/pdf", new byte[] {1, 2}));

        assertThat(result.contenu()).isEqualTo("Renvoi au 12");
        assertThat(result.nomFichier()).isEqualTo("plumitif.pdf");
    }

    /** A format outside FORMATS_GED is refused: a report attachment follows GED rules. */
    @Test
    void deposerCompteRendu_refuseUnePieceDeFormatNonAutorise() {
        Audience audience = Audience.builder().id(7L).dossier(dossier).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(audienceRepository.findById(7L)).thenReturn(Optional.of(audience));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));

        var requete = new DeposerCompteRenduRequest(1L, 7L, "Renvoi", null);
        var fichier = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] {1});
        assertThatThrownBy(() -> service.deposerCompteRendu(
                requete, fichier))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(minioStorageService);
    }

    // Other publication upload (US 5.3)

    @Test
    void deposerAutre_rejectsEmptyFile() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));
        MockMultipartFile vide = new MockMultipartFile("file", "", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.deposerAutre(1L, TypeAutrePublication.PIECE, vide, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void deposerAutre_nominalStoresFileAndNotifiesAssistante() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));
        MockMultipartFile fichier = new MockMultipartFile("file", "piece.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(minioStorageService.deposer(1L, fichier)).thenReturn("dossiers/1/piece.pdf");
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> {
            AutrePublication p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });
        when(utilisateurRepository.findByProfilCodeAndActifTrue("ASSISTANTE")).thenReturn(List.of(assistante));

        PublicationResponse result = service.deposerAutre(1L, TypeAutrePublication.PIECE, fichier, null);

        assertThat(result.typeDocument()).isEqualTo(TypeAutrePublication.PIECE);
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(assistante), any());
    }

    // Validation (US 5.2, generalized to both subtypes)

    @Test
    void valider_rejectsAlreadyDecided() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.VALIDE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        var requete = new ValiderPublicationRequest(StatutPublication.VALIDE, null);
        assertThatThrownBy(() -> service.valider(100L, requete))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void valider_rejectionRequiresMotif() {
        // Rejection no longer exists for a report: the reason rule is checked where rejection remains
        // (RG-INT-06).
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        var requete = new ValiderPublicationRequest(StatutPublication.REJETE, null);
        assertThatThrownBy(() -> service.valider(100L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("motif");
    }

    @Test
    void valider_rejectionNotifiesAvocat() {
        // Rejection no longer applies to a report: the notification is tested where it remains.
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurRepository.findByKeycloakId("kc-avocat")).thenReturn(Optional.of(utilisateurAvocat));

        PublicationResponse result = service.valider(100L, new ValiderPublicationRequest(StatutPublication.REJETE, "Contenu insuffisant"));

        assertThat(result.statutValidation()).isEqualTo(StatutPublication.REJETE);
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(utilisateurAvocat), any());
    }

    @Test
    void valider_worksOnAutrePublicationToo() {
        Publication publication = AutrePublication.builder().id(101L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE).statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(101L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));

        PublicationResponse result = service.valider(101L, new ValiderPublicationRequest(StatutPublication.VALIDE, null));

        assertThat(result.statutValidation()).isEqualTo(StatutPublication.VALIDE);
        assertThat(result.type()).isEqualTo(com.afriland.audiences.publication.domain.TypePublication.AUTRE);
    }

    // Consultation (US 5.4)

    /**
     * An uploaded OTHER publication stays closed: it carries the restricted regime. A report is open to
     * all (see below).
     */
    @Test
    void obtenir_refuseUneAutrePublicationDeposee() {
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        assertThatThrownBy(() -> service.obtenir(100L)).isInstanceOf(ApiException.class);
    }

    @Test
    void obtenir_refuseUneAutrePublicationRejetee() {
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.REJETE).build();
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        assertThatThrownBy(() -> service.obtenir(100L)).isInstanceOf(ApiException.class);
    }

    @Test
    void obtenir_rejectsRestrictedAccessForOtherUser() {
        Utilisateur destinataire = Utilisateur.builder().id(60L).build();
        Utilisateur autre = Utilisateur.builder().id(61L).build();
        // The restriction no longer applies to a report: it is tested on the other kind.
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.VALIDE).restrictionAcces(destinataire).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(currentUserService.getCurrentUser()).thenReturn(autre);

        assertThatThrownBy(() -> service.obtenir(100L)).isInstanceOf(ApiException.class);
    }

    @Test
    void obtenir_allowsRestrictedAccessForMatchingUser() {
        Utilisateur destinataire = Utilisateur.builder().id(60L).build();
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.VALIDE).restrictionAcces(destinataire).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(currentUserService.getCurrentUser()).thenReturn(destinataire);

        PublicationResponse result = service.obtenir(100L);

        assertThat(result.statutValidation()).isEqualTo(StatutPublication.VALIDE);
    }

    @Test
    void obtenir_allowsAnyoneWhenNoRestriction() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.VALIDE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        PublicationResponse result = service.obtenir(100L);

        assertThat(result.statutValidation()).isEqualTo(StatutPublication.VALIDE);
        verify(currentUserService, never()).getCurrentUser();
    }

    @Test
    void obtenir_letsTheAssistanteReadADeposePublicationAndItsFile() {
        String cle = "dossiers/1/123e4567-e89b-12d3-a456-426614174000-piece.pdf";
        Publication publication = AutrePublication.builder().id(101L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE).cheminStockage(cle).statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(101L)).thenReturn(Optional.of(publication));
        when(currentUserService.getCurrentUser()).thenReturn(assistante);
        when(minioStorageService.urlTelechargement(cle)).thenReturn("http://minio/piece.pdf?signature");

        PublicationResponse result = service.obtenir(101L);

        assertThat(result.statutValidation()).isEqualTo(StatutPublication.DEPOSE);
        assertThat(result.urlTelechargement()).isEqualTo("http://minio/piece.pdf?signature");
        assertThat(result.nomFichier()).isEqualTo("piece.pdf");
        assertThat(result.referenceDossier()).isEqualTo("DOS-2026-001");
        assertThat(result.avocatNom()).isEqualTo("Me Fotso");
    }

    @Test
    void obtenir_letsTheDjReadARestrictedPublication() {
        Utilisateur destinataire = Utilisateur.builder().id(60L).build();
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.VALIDE).restrictionAcces(destinataire).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(currentUserService.getCurrentUser()).thenReturn(dj);

        assertThat(service.obtenir(100L).restrictionAccesId()).isEqualTo(60L);
    }

    @Test
    void obtenir_exposesTheCommentsAndTheCorrespondence() {
        Utilisateur juristeNomme = Utilisateur.builder().id(10L).prenom("Jean").nom("Juriste").build();
        Utilisateur djNomme = Utilisateur.builder().id(40L).prenom("Marie").nom("Directrice").build();
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.VALIDE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(commentairePublicationRepository.findByPublicationIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(
                CommentairePublication.builder().id(200L).publication(publication).auteur(juristeNomme).contenu("Pièce incomplète").build()));
        when(correspondanceAvocatRepository.findByPublicationId(100L)).thenReturn(Optional.of(
                CorrespondanceAvocat.builder().id(300L).publication(publication).auteur(djNomme).contenu("Merci de compléter").build()));

        PublicationResponse result = service.obtenir(100L);

        assertThat(result.commentaires()).singleElement().satisfies(c -> {
            assertThat(c.auteurNom()).isEqualTo("Jean Juriste");
            assertThat(c.contenu()).isEqualTo("Pièce incomplète");
        });
        assertThat(result.correspondance().auteurNom()).isEqualTo("Marie Directrice");
    }

    /**
     * Only the recipient designated by the lawyer is notified; case assignment does not replace it. This
     * restriction no longer applies to reports, so it is tested on the other kind of publication.
     */
    @Test
    void valider_neProvientQueLeDestinataireDesigne() {
        Utilisateur destinataire = Utilisateur.builder().id(60L).build();
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.DEPOSE).restrictionAcces(destinataire).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));

        service.valider(100L, new ValiderPublicationRequest(StatutPublication.VALIDE, null));

        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(destinataire), any());
        verify(dossierJuristeRepository, never()).findByDossierId(any());
    }

    // Comment / correspondence (US 5.5)

    @Test
    void commenter_notifiesDjAndDja() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(commentairePublicationRepository.save(any())).thenAnswer(inv -> {
            var c = inv.getArgument(0, com.afriland.audiences.publication.domain.CommentairePublication.class);
            c.setId(200L);
            return c;
        });
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(dj));
        Utilisateur dja = Utilisateur.builder().id(41L).build();
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of(dja));

        CommentaireResponse result = service.commenter(100L, new CommentaireRequest("Cette pièce me semble incomplète"));

        assertThat(result.publicationId()).isEqualTo(100L);
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(dj), any());
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(dja), any());
    }

    @Test
    void transmettreCorrespondance_rejectsDuplicate() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(correspondanceAvocatRepository.existsByPublicationId(100L)).thenReturn(true);

        var requete = new CorrespondanceRequest("Réponse");
        assertThatThrownBy(() -> service.transmettreCorrespondance(100L, requete))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void transmettreCorrespondance_nominalNotifiesAvocat() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(correspondanceAvocatRepository.existsByPublicationId(100L)).thenReturn(false);
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(correspondanceAvocatRepository.save(any())).thenAnswer(inv -> {
            CorrespondanceAvocat c = inv.getArgument(0);
            c.setId(300L);
            return c;
        });
        when(utilisateurRepository.findByKeycloakId("kc-avocat")).thenReturn(Optional.of(utilisateurAvocat));

        var result = service.transmettreCorrespondance(100L, new CorrespondanceRequest("Votre pièce a été prise en compte"));

        assertThat(result.publicationId()).isEqualTo(100L);
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(utilisateurAvocat), any());
    }

    /** The four filter combinations route to four distinct queries. */
    @Test
    void lister_aiguilleSelonLesFiltres() {
        // The Assistant instructs: no visibility restriction applies to her.
        when(currentUserService.getCurrentUser()).thenReturn(assistante);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        var page = new org.springframework.data.domain.PageImpl<Publication>(
                List.of(CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                        .statutValidation(StatutPublication.DEPOSE).build()),
                pageable, 1);

        when(publicationRepository.findByDossierIdAndStatutValidation(1L, StatutPublication.DEPOSE, pageable)).thenReturn(page);
        assertThat(service.lister(1L, StatutPublication.DEPOSE, pageable).content()).hasSize(1);

        when(publicationRepository.findByDossierId(1L, pageable)).thenReturn(page);
        assertThat(service.lister(1L, null, pageable).totalElements()).isEqualTo(1);

        when(publicationRepository.findByStatutValidation(StatutPublication.DEPOSE, pageable)).thenReturn(page);
        assertThat(service.lister(null, StatutPublication.DEPOSE, pageable).content()).hasSize(1);

        when(publicationRepository.findAll(pageable)).thenReturn(page);
        assertThat(service.lister(null, null, pageable).content()).hasSize(1);
    }

    // RG-INT-06: reports are open to all

    /** Uploaded, not yet acknowledged by the Assistant: any authorized user can already read it. */
    @Test
    void obtenir_ouvreUnCompteRenduDeposeATousLesHabilites() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        assertThat(service.obtenir(100L).statutValidation()).isEqualTo(StatutPublication.DEPOSE);
        // No identity is checked: the rule no longer depends on who is looking.
        verify(dossierJuristeRepository, never()).existsByDossierIdAndUtilisateurId(anyLong(), anyLong());
    }

    /**
     * A restriction set on a report no longer closes it. Upload now refuses it (see
     * {@code deposerCompteRendu_refuseUneReserve}), but existing reports may carry one: it must exclude
     * nobody.
     */
    @Test
    void obtenir_ignoreUneReserveAncienneSurUnCompteRendu() {
        Utilisateur designe = Utilisateur.builder().id(11L)
                .profil(Profil.builder().code("JURISTE").build()).build();
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.DEPOSE).restrictionAcces(designe).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        assertThat(service.obtenir(100L).id()).isEqualTo(100L);
    }

    /** The restriction still excludes on another publication. */
    @Test
    void obtenir_laReserveEcarteEncoreSurUneAutrePublication() {
        Utilisateur designe = Utilisateur.builder().id(11L)
                .profil(Profil.builder().code("JURISTE").build()).build();
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.VALIDE).restrictionAcces(designe).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);

        assertThatThrownBy(() -> service.obtenir(100L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("restreinte");
    }

    /** A restriction on a report is refused on upload, not silently ignored. */
    @Test
    void deposerCompteRendu_refuseUneReserve() {
        Audience audience = Audience.builder().id(7L).dossier(dossier).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(audienceRepository.findById(7L)).thenReturn(Optional.of(audience));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));

        var requete = new DeposerCompteRenduRequest(1L, 7L, "Contenu", 60L);
        assertThatThrownBy(() -> service.deposerCompteRendu(
                requete, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ne se réserve pas");
        verify(publicationRepository, never()).save(any(Publication.class));
    }

    /** Upload notifies the case's legal officers: access is open to all, the alert stays targeted. */
    @Test
    void deposerCompteRendu_alerteLesJuristesDuDossier() {
        Audience audience = Audience.builder().id(7L).dossier(dossier).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(audienceRepository.findById(7L)).thenReturn(Optional.of(audience));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> {
            CompteRenduAudience p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(
                List.of(DossierJuriste.builder().dossier(dossier).utilisateur(juriste).build()));

        service.deposerCompteRendu(new DeposerCompteRenduRequest(1L, 7L, "Contenu", null), null);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(juriste),
                message.capture());
        assertThat(message.getValue()).contains("consultable dès maintenant");
    }

    /** A report cannot be rejected: the Assistant acknowledges it, or nothing. */
    @Test
    void valider_refuseLeRejetDUnCompteRendu() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        var requete = new ValiderPublicationRequest(StatutPublication.REJETE, "Récit incomplet");
        assertThatThrownBy(() -> service.valider(100L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ne se rejette pas");
        verify(publicationRepository, never()).save(any(Publication.class));
    }

    /**
     * The lawyer is notified of an acknowledgement by an event that says so. {@code PUBLICATION_ARBITREE}
     * announces a decision on the document; there is none for a report, so sending it would make the
     * lawyer wait for a verdict that never comes.
     */
    @Test
    void valider_previentLAvocatDeLAccuseDeReceptionSurUnCompteRendu() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));

        service.valider(100L, new ValiderPublicationRequest(StatutPublication.VALIDE, null));

        verify(notificationPrestataireService).notifier(
                eq(EvenementPrestataire.COMPTE_RENDU_RECU), eq(avocat), eq(1L),
                eq("publication-100-reception"));
    }

    /** Another publication is indeed arbitrated: the former event remains. */
    @Test
    void valider_previentLAvocatDUnArbitrageSurUneAutrePublication() {
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(List.of());

        service.valider(100L, new ValiderPublicationRequest(StatutPublication.VALIDE, null));

        verify(notificationPrestataireService).notifier(
                eq(EvenementPrestataire.PUBLICATION_ARBITREE), eq(avocat), eq(1L),
                eq("publication-100-arbitrage"));
    }

    /** Rejection remains on another publication, whose compliance is checked. */
    @Test
    void valider_accepteLeRejetDUneAutrePublication() {
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));

        service.valider(100L, new ValiderPublicationRequest(StatutPublication.REJETE, "Pièce illisible"));

        assertThat(publication.getStatutValidation()).isEqualTo(StatutPublication.REJETE);
        assertThat(publication.getMotifRejet()).isEqualTo("Pièce illisible");
    }

    /**
     * The acknowledgement does not re-alert: the case's legal officers were notified on upload, and
     * everyone else could read it from then on.
     */
    @Test
    void valider_neRealertePasSurUnCompteRendu() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));

        service.valider(100L, new ValiderPublicationRequest(StatutPublication.VALIDE, null));

        verify(alerteService, never()).declencher(any(), anyLong(), any(), anyString());
        verify(dossierJuristeRepository, never()).findByDossierId(anyLong());
    }

    /** Another publication is still notified on validation. */
    @Test
    void valider_alerteEncoreLesJuristesSurUneAutrePublication() {
        Publication publication = AutrePublication.builder().id(100L).dossier(dossier).avocat(avocat)
                .typeDocument(TypeAutrePublication.PIECE)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(
                List.of(DossierJuriste.builder().dossier(dossier).utilisateur(juriste).build()));

        service.valider(100L, new ValiderPublicationRequest(StatutPublication.VALIDE, null));

        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(juriste), anyString());
    }

    /** For a non-instructor, the list goes through the visibility query. */
    @Test
    void lister_appliqueLaVisibiliteAuxHabilites() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        var page = new org.springframework.data.domain.PageImpl<Publication>(List.of(), pageable, 0);
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(publicationRepository.findVisiblesParLesHabilites(1L, null, pageable)).thenReturn(page);

        service.lister(1L, null, pageable);

        verify(publicationRepository).findVisiblesParLesHabilites(1L, null, pageable);
        verify(publicationRepository, never()).findByDossierId(anyLong(), any());
    }

    /** The DJ instructs: they see the queue like the Assistant. */
    @Test
    void lister_neRestreintPasLeDj() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        var page = new org.springframework.data.domain.PageImpl<Publication>(List.of(), pageable, 0);
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(publicationRepository.findByDossierId(1L, pageable)).thenReturn(page);

        service.lister(1L, null, pageable);

        verify(publicationRepository).findByDossierId(1L, pageable);
        verify(publicationRepository, never()).findVisiblesParLesHabilites(any(), any(), any());
    }
}
