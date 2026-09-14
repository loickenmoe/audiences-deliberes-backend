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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    // Q-98 : FORMATS_GED / TAILLE_MAX_GED pilotent aussi la pièce jointe d'un compte rendu.
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

    @BeforeEach
    void setUp() {
        service = new PublicationService(publicationRepository, commentairePublicationRepository,
                correspondanceAvocatRepository, dossierRepository, audienceRepository,
                new CompteAvocatService(avocatRepository, utilisateurRepository),
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

    // --- Dépôt compte rendu (US 5.1) ---

    @Test
    void deposerCompteRendu_rejectsAudienceNotAttachedToDossier() {
        Dossier autreDossier = Dossier.builder().id(2L).build();
        Audience audience = Audience.builder().id(7L).dossier(autreDossier).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(audienceRepository.findById(7L)).thenReturn(Optional.of(audience));

        assertThatThrownBy(() -> service.deposerCompteRendu(new DeposerCompteRenduRequest(1L, 7L, "Contenu", null), null))
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

        // Sans pièce jointe : le cas nominal d'US 5.1, inchangé par Q-98.
        PublicationResponse result =
                service.deposerCompteRendu(new DeposerCompteRenduRequest(1L, 7L, "Contenu du CR", null), null);

        assertThat(result.statutValidation()).isEqualTo(StatutPublication.DEPOSE);
        assertThat(result.audienceId()).isEqualTo(7L);
        assertThat(result.nomFichier()).as("aucune pièce n'a été jointe").isNull();
        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(assistante), any());
        verifyNoInteractions(minioStorageService);
    }

    /**
     * Q-98 : la pièce jointe est facultative, mais quand elle est là elle passe par les mêmes
     * contrôles que la GED et atterrit dans le même stockage — le récit reste obligatoire.
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
        // Clé réelle de MinioStorageService : {uuid}-{nom}, dont le mapper retire le préfixe.
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

    /** Un format hors FORMATS_GED est refusé : la pièce d'un compte rendu suit les règles de la GED. */
    @Test
    void deposerCompteRendu_refuseUnePieceDeFormatNonAutorise() {
        Audience audience = Audience.builder().id(7L).dossier(dossier).build();
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(audienceRepository.findById(7L)).thenReturn(Optional.of(audience));
        when(currentUserService.getCurrentUser()).thenReturn(utilisateurAvocat);
        when(avocatRepository.findByCompteKeycloak("kc-avocat")).thenReturn(Optional.of(avocat));

        assertThatThrownBy(() -> service.deposerCompteRendu(
                new DeposerCompteRenduRequest(1L, 7L, "Renvoi", null),
                new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] {1})))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(minioStorageService);
    }

    // --- Dépôt autre publication (US 5.3) ---

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

    // --- Validation (US 5.2, généralisée aux deux sous-types — Q-55) ---

    @Test
    void valider_rejectsAlreadyDecided() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.VALIDE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        assertThatThrownBy(() -> service.valider(100L, new ValiderPublicationRequest(StatutPublication.VALIDE, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void valider_rejectionRequiresMotif() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        assertThatThrownBy(() -> service.valider(100L, new ValiderPublicationRequest(StatutPublication.REJETE, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("motif");
    }

    @Test
    void valider_rejectionNotifiesAvocat() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
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

    // --- Consultation (US 5.4) ---

    @Test
    void obtenir_rejectsNonValideAsNotFound() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        assertThatThrownBy(() -> service.obtenir(100L)).isInstanceOf(ApiException.class);
    }

    @Test
    void obtenir_rejectsRejeteAsNotFound() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.REJETE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));

        assertThatThrownBy(() -> service.obtenir(100L)).isInstanceOf(ApiException.class);
    }

    @Test
    void obtenir_rejectsRestrictedAccessForOtherUser() {
        Utilisateur destinataire = Utilisateur.builder().id(60L).build();
        Utilisateur autre = Utilisateur.builder().id(61L).build();
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.VALIDE).restrictionAcces(destinataire).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(currentUserService.getCurrentUser()).thenReturn(autre);

        assertThatThrownBy(() -> service.obtenir(100L)).isInstanceOf(ApiException.class);
    }

    @Test
    void obtenir_allowsRestrictedAccessForMatchingUser() {
        Utilisateur destinataire = Utilisateur.builder().id(60L).build();
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
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
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
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

    @Test
    void valider_notifiesTheJuristesOfTheDossier() {
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.DEPOSE).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(List.of(DossierJuriste.builder().utilisateur(juriste).build()));

        service.valider(100L, new ValiderPublicationRequest(StatutPublication.VALIDE, null));

        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(juriste), any());
    }

    @Test
    void valider_notifiesOnlyTheDesignatedReaderOfARestrictedPublication() {
        Utilisateur destinataire = Utilisateur.builder().id(60L).build();
        Publication publication = CompteRenduAudience.builder().id(100L).dossier(dossier).avocat(avocat)
                .statutValidation(StatutPublication.DEPOSE).restrictionAcces(destinataire).build();
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));

        service.valider(100L, new ValiderPublicationRequest(StatutPublication.VALIDE, null));

        verify(alerteService).declencher(eq(TypeAlerte.NOUVELLE_PUBLICATION), eq(1L), eq(destinataire), any());
        verify(dossierJuristeRepository, never()).findByDossierId(any());
    }

    // --- Commentaire / correspondance (US 5.5) ---

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

        assertThatThrownBy(() -> service.transmettreCorrespondance(100L, new CorrespondanceRequest("Réponse")))
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

    // ------------------------------------------------------------------ lister (M15, Q-68)

    /** Les quatre combinaisons de filtres aiguillent vers quatre requêtes distinctes. */
    @Test
    void lister_aiguilleSelonLesFiltres() {
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
}
