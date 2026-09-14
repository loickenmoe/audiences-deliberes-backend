package com.afriland.audiences.ged.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.domain.DemandeSuppression;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.FormatDocument;
import com.afriland.audiences.ged.domain.StatutSuppression;
import com.afriland.audiences.ged.mapper.DemandeSuppressionMapper;
import com.afriland.audiences.ged.repository.DemandeSuppressionRepository;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.web.dto.ApprobationSuppressionRequest;
import com.afriland.audiences.ged.web.dto.DemandeSuppressionResponse;
import com.afriland.audiences.ged.web.dto.DocumentResponse;
import com.afriland.audiences.ged.web.dto.RapportJournalierResponse;
import com.afriland.audiences.ged.web.dto.SuppressionResult;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GedServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DemandeSuppressionRepository demandeSuppressionRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private DossierJuristeRepository dossierJuristeRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private HistoriqueActionRepository historiqueActionRepository;
    @Mock private AudienceRepository audienceRepository;
    @Mock private ConfigurationService configurationService;
    @Mock private AlerteService alerteService;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private CurrentUserService currentUserService;
    @Mock private MinioStorageService minioStorageService;

    private final DemandeSuppressionMapper demandeSuppressionMapper = demande -> new DemandeSuppressionResponse(
            demande.getId(), demande.getDocument().getId(), demande.getDocument().getNomFichier(),
            demande.getDocument().getTypeDocument(),
            demande.getDocument().getDossier() != null ? demande.getDocument().getDossier().getId() : null,
            demande.getDocument().getDossier() != null ? demande.getDocument().getDossier().getReference() : null,
            demande.getDemandeur().getId(), demande.getMotif(),
            demande.getStatut(), demande.getValidePar() != null ? demande.getValidePar().getId() : null,
            demande.getMotifRejet(), demande.getDateDemande(), demande.getDateDecision(), demande.getDateDerniereRelance());

    private GedService service;
    private Dossier dossier;
    private Utilisateur juriste;
    private Utilisateur dj;

    @BeforeEach
    void setUp() {
        service = new GedService(documentRepository, demandeSuppressionRepository, dossierRepository,
                dossierJuristeRepository, utilisateurRepository, historiqueActionRepository, audienceRepository,
                configurationService, alerteService, historiqueActionService, currentUserService, minioStorageService,
                new ValidationFichierService(configurationService), demandeSuppressionMapper);

        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        juriste = Utilisateur.builder().id(10L).profil(Profil.builder().code("JURISTE").build()).build();
        dj = Utilisateur.builder().id(20L).profil(Profil.builder().code("DJ").build()).build();
    }

    // ------------------------------------------------------------------ uploaderDocument

    @Test
    void uploaderDocument_rejectsUnsupportedFormat() {
        // Extension inconnue de FormatDocument (échoue avant même la consultation de FORMATS_GED).
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        MockMultipartFile fichier = new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.uploaderDocument(1L, "Pièce", fichier))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("non conforme");
        verify(minioStorageService, never()).deposer(any(), any());
    }

    @Test
    void uploaderDocument_rejectsFormatDisabledByConfiguration() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(configurationService.getOrThrow("FORMATS_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("PDF,PNG,JPG,XLSX").build());
        MockMultipartFile fichier = new MockMultipartFile("file", "note.txt", "text/plain", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.uploaderDocument(1L, "Pièce", fichier))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("désactivé");
        verify(minioStorageService, never()).deposer(any(), any());
    }

    @Test
    void uploaderDocument_rejectsOversizedFile() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(configurationService.getOrThrow("FORMATS_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("PDF,PNG,JPG,TXT,XLSX").build());
        when(configurationService.getOrThrow("TAILLE_MAX_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("10").build());
        MockMultipartFile fichier = new MockMultipartFile("file", "gros.pdf", "application/pdf", new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});

        assertThatThrownBy(() -> service.uploaderDocument(1L, "Pièce", fichier))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("supérieur au maximum");
    }

    @Test
    void uploaderDocument_nominalNotifiesJuristesCotes() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(configurationService.getOrThrow("FORMATS_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("PDF,PNG,JPG,TXT,XLSX").build());
        when(configurationService.getOrThrow("TAILLE_MAX_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("10485760").build());
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(minioStorageService.deposer(eq(1L), any())).thenReturn("dossiers/1/uuid-piece.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(100L);
            return d;
        });
        DossierJuriste affectation = DossierJuriste.builder().dossier(dossier).utilisateur(juriste).build();
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(List.of(affectation));
        MockMultipartFile fichier = new MockMultipartFile("file", "piece.pdf", "application/pdf", new byte[]{1, 2, 3});

        DocumentResponse result = service.uploaderDocument(1L, "Pièce", fichier);

        assertThat(result.format()).isEqualTo(FormatDocument.PDF);
        assertThat(result.estSupprime()).isFalse();
        assertThat(result.urlTelechargement()).isNull();
        verify(alerteService).declencher(eq(TypeAlerte.DOCUMENT_CHARGE), eq(1L), eq(juriste), any());
    }

    // ------------------------------------------------------------------ obtenirDocument

    @Test
    void obtenirDocument_notFoundWhenDeleted() {
        Document document = Document.builder().id(5L).dossier(dossier).estSupprime(true).build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.obtenirDocument(5L)).isInstanceOf(ApiException.class);
    }

    @Test
    void obtenirDocument_returnsPresignedUrl() {
        Document document = Document.builder().id(5L).dossier(dossier).nomFichier("piece.pdf")
                .format(FormatDocument.PDF).poids(100L).auteurChargement(juriste).estSupprime(false)
                .cheminStockage("dossiers/1/uuid-piece.pdf").build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        when(minioStorageService.urlTelechargement("dossiers/1/uuid-piece.pdf")).thenReturn("http://minio/presigned");

        DocumentResponse result = service.obtenirDocument(5L);

        assertThat(result.urlTelechargement()).isEqualTo("http://minio/presigned");
    }

    // ------------------------------------------------------------------ supprimerOuDemander

    @Test
    void supprimerOuDemander_djDeletesImmediately() {
        Document document = Document.builder().id(5L).dossier(dossier).nomFichier("piece.pdf")
                .auteurChargement(juriste).estSupprime(false).cheminStockage("cle").build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        SuppressionResult result = service.supprimerOuDemander(5L);

        assertThat(result.immediate()).isTrue();
        assertThat(result.document().estSupprime()).isTrue();
        verify(minioStorageService).supprimer("cle");
        verify(alerteService).declencher(eq(TypeAlerte.SUPPRESSION_DOCUMENT), eq(1L), eq(juriste), any());
    }

    /** Q-79 : sans cela, la demande restait EN_ATTENTE, indécidable (404) et relancée toutes les 72 h. */
    @Test
    void supprimerOuDemander_suppressionDirecteFermeLaDemandeEnAttente() {
        Document document = Document.builder().id(5L).dossier(dossier).nomFichier("piece.pdf")
                .auteurChargement(juriste).estSupprime(false).cheminStockage("cle").build();
        DemandeSuppression enAttente = DemandeSuppression.builder().id(200L).document(document).demandeur(juriste)
                .statut(StatutSuppression.EN_ATTENTE).build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(demandeSuppressionRepository.findByDocumentIdAndStatut(5L, StatutSuppression.EN_ATTENTE))
                .thenReturn(Optional.of(enAttente));

        service.supprimerOuDemander(5L);

        assertThat(enAttente.getStatut()).isEqualTo(StatutSuppression.APPROUVEE);
        assertThat(enAttente.getValidePar()).isEqualTo(dj);
        assertThat(enAttente.getDateDecision()).isNotNull();
        verify(demandeSuppressionRepository).save(enAttente);
    }

    /** Q-79 : la file dit quoi supprimer — le fichier et son dossier, pas un identifiant. */
    @Test
    void listerDemandesSuppression_nommeLeDocumentEtLeDossier() {
        Document document = Document.builder().id(1L).dossier(dossier).nomFichier("pv-transfert.pdf")
                .typeDocument("PV_TRANSFERT").build();
        when(demandeSuppressionRepository.findByStatutOrderByDateDemandeAsc(StatutSuppression.EN_ATTENTE))
                .thenReturn(List.of(DemandeSuppression.builder().id(9L).document(document).demandeur(juriste)
                        .statut(StatutSuppression.EN_ATTENTE).build()));

        DemandeSuppressionResponse demande = service.listerDemandesSuppression(StatutSuppression.EN_ATTENTE).get(0);

        assertThat(demande.documentNom()).isEqualTo("pv-transfert.pdf");
        assertThat(demande.typeDocument()).isEqualTo("PV_TRANSFERT");
        assertThat(demande.dossierId()).isEqualTo(1L);
        assertThat(demande.referenceDossier()).isEqualTo("DOS-2026-001");
    }

    @Test
    void supprimerOuDemander_nonAuthorJuristeForbidden() {
        Utilisateur autreJuriste = Utilisateur.builder().id(11L).profil(Profil.builder().code("JURISTE").build()).build();
        Document document = Document.builder().id(5L).dossier(dossier).auteurChargement(juriste).estSupprime(false).build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        when(currentUserService.getCurrentUser()).thenReturn(autreJuriste);

        assertThatThrownBy(() -> service.supprimerOuDemander(5L)).isInstanceOf(ApiException.class);
    }

    @Test
    void supprimerOuDemander_authorCreatesDemandeAndNotifiesDjAndDja() {
        Utilisateur dja = Utilisateur.builder().id(21L).profil(Profil.builder().code("DJA").build()).build();
        Document document = Document.builder().id(5L).dossier(dossier).nomFichier("piece.pdf")
                .auteurChargement(juriste).estSupprime(false).build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(demandeSuppressionRepository.existsByDocumentIdAndStatut(5L, StatutSuppression.EN_ATTENTE)).thenReturn(false);
        when(demandeSuppressionRepository.save(any(DemandeSuppression.class))).thenAnswer(inv -> {
            DemandeSuppression d = inv.getArgument(0);
            d.setId(200L);
            return d;
        });
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(new java.util.ArrayList<>(List.of(dj)));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of(dja));

        SuppressionResult result = service.supprimerOuDemander(5L);

        assertThat(result.immediate()).isFalse();
        assertThat(result.demande().statut()).isEqualTo(StatutSuppression.EN_ATTENTE);
        verify(alerteService).declencher(eq(TypeAlerte.SUPPRESSION_DOCUMENT), eq(1L), eq(dj), any());
        verify(alerteService).declencher(eq(TypeAlerte.SUPPRESSION_DOCUMENT), eq(1L), eq(dja), any());
    }

    @Test
    void supprimerOuDemander_rejectsDuplicatePendingDemande() {
        Document document = Document.builder().id(5L).dossier(dossier).auteurChargement(juriste).estSupprime(false).build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(demandeSuppressionRepository.existsByDocumentIdAndStatut(5L, StatutSuppression.EN_ATTENTE)).thenReturn(true);

        assertThatThrownBy(() -> service.supprimerOuDemander(5L)).isInstanceOf(ApiException.class);
    }

    // ------------------------------------------------------------------ approuverOuRejeter

    @Test
    void approuverOuRejeter_approvalDeletesDocument() {
        Document document = Document.builder().id(5L).dossier(dossier).nomFichier("piece.pdf")
                .estSupprime(false).cheminStockage("cle").build();
        DemandeSuppression demande = DemandeSuppression.builder().id(200L).document(document).demandeur(juriste)
                .statut(StatutSuppression.EN_ATTENTE).build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        when(demandeSuppressionRepository.findByDocumentIdAndStatut(5L, StatutSuppression.EN_ATTENTE)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(demandeSuppressionRepository.save(any(DemandeSuppression.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandeSuppressionResponse result = service.approuverOuRejeter(5L, new ApprobationSuppressionRequest(StatutSuppression.APPROUVEE, null));

        assertThat(result.statut()).isEqualTo(StatutSuppression.APPROUVEE);
        assertThat(document.getEstSupprime()).isTrue();
        verify(minioStorageService).supprimer("cle");
        verify(alerteService).declencher(eq(TypeAlerte.SUPPRESSION_DOCUMENT), eq(1L), eq(juriste), any());
    }

    @Test
    void approuverOuRejeter_rejectionRequiresMotif() {
        Document document = Document.builder().id(5L).dossier(dossier).estSupprime(false).build();
        DemandeSuppression demande = DemandeSuppression.builder().id(200L).document(document).demandeur(juriste)
                .statut(StatutSuppression.EN_ATTENTE).build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        when(demandeSuppressionRepository.findByDocumentIdAndStatut(5L, StatutSuppression.EN_ATTENTE)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dj);

        assertThatThrownBy(() -> service.approuverOuRejeter(5L, new ApprobationSuppressionRequest(StatutSuppression.REJETEE, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("motif");
        assertThat(document.getEstSupprime()).isFalse();
    }

    @Test
    void approuverOuRejeter_rejectionKeepsDocumentIntact() {
        Document document = Document.builder().id(5L).dossier(dossier).nomFichier("piece.pdf").estSupprime(false).build();
        DemandeSuppression demande = DemandeSuppression.builder().id(200L).document(document).demandeur(juriste)
                .statut(StatutSuppression.EN_ATTENTE).build();
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        when(demandeSuppressionRepository.findByDocumentIdAndStatut(5L, StatutSuppression.EN_ATTENTE)).thenReturn(Optional.of(demande));
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(demandeSuppressionRepository.save(any(DemandeSuppression.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandeSuppressionResponse result = service.approuverOuRejeter(5L, new ApprobationSuppressionRequest(StatutSuppression.REJETEE, "Pièce encore utile"));

        assertThat(result.statut()).isEqualTo(StatutSuppression.REJETEE);
        assertThat(result.motifRejet()).isEqualTo("Pièce encore utile");
        assertThat(document.getEstSupprime()).isFalse();
        verify(documentRepository, never()).save(any());
        verify(minioStorageService, never()).supprimer(any());
    }

    // ------------------------------------------------------------------ rapportJournalier

    @Test
    void rapportJournalier_returnsZeroesWhenNoData() {
        LocalDate date = LocalDate.of(2026, 9, 6);
        when(historiqueActionRepository.countDistinctDossierIdByDateActionBetween(any(), any())).thenReturn(0L);
        when(documentRepository.countByDateChargementBetween(any(), any())).thenReturn(0L);
        when(dossierRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(audienceRepository.findByStatutAndDatePlanifiee(any(), eq(date))).thenReturn(List.of());

        RapportJournalierResponse result = service.rapportJournalier(date);

        assertThat(result.dossiersManipules()).isZero();
        assertThat(result.piecesAjoutees()).isZero();
        assertThat(result.affairesNouvelles()).isZero();
        assertThat(result.planificationActes()).isEmpty();
    }

    // ------------------------------------------------------------------ listerDemandesSuppression (M15, Q-68)

    @Test
    void listerDemandesSuppression_filtreParStatut() {
        Document document = Document.builder().id(1L).dossier(dossier).nomFichier("piece.pdf").build();
        when(demandeSuppressionRepository.findByStatutOrderByDateDemandeAsc(StatutSuppression.EN_ATTENTE))
                .thenReturn(List.of(DemandeSuppression.builder().id(9L).document(document).demandeur(juriste)
                        .motif("Doublon").statut(StatutSuppression.EN_ATTENTE).build()));

        var result = service.listerDemandesSuppression(StatutSuppression.EN_ATTENTE);

        assertThat(result).singleElement()
                .satisfies(d -> assertThat(d.statut()).isEqualTo(StatutSuppression.EN_ATTENTE));
        verify(demandeSuppressionRepository, never()).findByOrderByDateDemandeDesc();
    }

    @Test
    void listerDemandesSuppression_sansFiltre_retourneToutLHistorique() {
        Document document = Document.builder().id(1L).dossier(dossier).nomFichier("piece.pdf").build();
        when(demandeSuppressionRepository.findByOrderByDateDemandeDesc())
                .thenReturn(List.of(
                        DemandeSuppression.builder().id(9L).document(document).demandeur(juriste)
                                .motif("Doublon").statut(StatutSuppression.APPROUVEE).validePar(dj).build(),
                        DemandeSuppression.builder().id(8L).document(document).demandeur(juriste)
                                .motif("Erreur").statut(StatutSuppression.REJETEE).validePar(dj)
                                .motifRejet("Pièce utile").build()));

        assertThat(service.listerDemandesSuppression(null)).hasSize(2);
    }
}
