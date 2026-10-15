package com.afriland.audiences.portail.service;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.FormatDocument;
import com.afriland.audiences.ged.domain.StatutPartageAvocat;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.ged.service.ValidationFichierService;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.mapper.DemandeConstitutionMapper;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.publication.mapper.PublicationMapper;
import com.afriland.audiences.publication.repository.CorrespondanceAvocatRepository;
import com.afriland.audiences.publication.repository.PublicationRepository;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ce que l'avocat lit des pièces et des notes de son dossier (QF-64).
 *
 * <p><b>La règle, arbitrée le 2026-10-15 :</b> affecté <b>et constitué</b>. L'affectation ne fait
 * pas le mandat - c'est la lettre signée par le SH qui fait de l'avocat le représentant de la
 * banque. Ces tests gardent les trois portes : pas affecté (403), affecté sans être constitué (rien
 * ne s'ouvre), constitué (les pièces partagées, et elles seules).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortailAvocatPiecesTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private CompteAvocatService compteAvocatService;
    @Mock private DossierRepository dossierRepository;
    @Mock private DossierAvocatRepository dossierAvocatRepository;
    @Mock private AudienceRepository audienceRepository;
    @Mock private PublicationRepository publicationRepository;
    @Mock private CorrespondanceAvocatRepository correspondanceAvocatRepository;
    @Mock private PublicationMapper publicationMapper;
    @Mock private DemandeConstitutionRepository demandeConstitutionRepository;
    @Mock private DemandeConstitutionMapper demandeConstitutionMapper;
    @Mock private MinioStorageService minioStorageService;
    @Mock private ValidationFichierService validationFichierService;
    @Mock private DocumentRepository documentRepository;
    @Mock private HistoriqueActionService historiqueActionService;

    private static final long DOSSIER = 3L;
    private static final long AVOCAT = 1L;

    private PortailAvocatService service;

    @BeforeEach
    void setUp() {
        service = new PortailAvocatService(currentUserService, compteAvocatService, dossierRepository,
                dossierAvocatRepository, audienceRepository, publicationRepository,
                correspondanceAvocatRepository, publicationMapper, demandeConstitutionRepository,
                demandeConstitutionMapper, minioStorageService, validationFichierService,
                documentRepository, historiqueActionService);
        Utilisateur compte = Utilisateur.builder().id(30L).nom("Avocat").prenom("Me").build();
        when(currentUserService.getCurrentUser()).thenReturn(compte);
        when(compteAvocatService.avocatDe(compte))
                .thenReturn(Optional.of(Avocat.builder().id(AVOCAT).nom("Me Avocat").build()));
    }

    private void affecte(boolean affecte) {
        when(dossierAvocatRepository.existsByDossierIdAndAvocatId(DOSSIER, AVOCAT)).thenReturn(affecte);
    }

    private void constitue(boolean constitue) {
        when(demandeConstitutionRepository.existsByDossierIdAndPrestataireIdAndStatut(
                DOSSIER, AVOCAT, StatutConstitution.VALIDEE)).thenReturn(constitue);
    }

    private Document piece() {
        return Document.builder().id(7L).nomFichier("assignation.pdf").typeDocument("Assignation")
                .format(FormatDocument.PDF).poids(1024L).cheminStockage("dossiers/3/uuid-assignation.pdf")
                .partageAvocat(StatutPartageAvocat.PARTAGE).estSupprime(false).build();
    }

    // ─────────────────────────────────────────────────────────────── la liste

    @Test
    @DisplayName("pas affecté au dossier : 403, et la GED n'est même pas interrogée")
    void pieces_nonAffecte_403() {
        affecte(false);

        assertThatThrownBy(() -> service.piecesDeMonDossier(DOSSIER)).isInstanceOf(ApiException.class);
        verify(documentRepository, never())
                .findByDossierIdAndPartageAvocatAndEstSupprimeFalseOrderByDateChargementDesc(anyLong(), any());
    }

    /**
     * <b>Affecté sans être constitué</b> : rien ne s'ouvre, et l'écran peut le dire. « Aucune pièce »
     * et « pas encore constitué » sont deux situations différentes - la seconde se règle par une
     * signature.
     */
    @Test
    @DisplayName("affecté sans être constitué : aucune pièce, et la raison est dite")
    void pieces_affecteNonConstitue() {
        affecte(true);
        constitue(false);

        var reponse = service.piecesDeMonDossier(DOSSIER);

        assertThat(reponse.constitue()).isFalse();
        assertThat(reponse.pieces()).isEmpty();
        verify(documentRepository, never())
                .findByDossierIdAndPartageAvocatAndEstSupprimeFalseOrderByDateChargementDesc(anyLong(), any());
    }

    /** La restriction est dans la requête : seules les pièces PARTAGÉES de CE dossier sont lues. */
    @Test
    @DisplayName("constitué : les pièces partagées de ce dossier, et elles seules")
    void pieces_constitue() {
        affecte(true);
        constitue(true);
        when(documentRepository.findByDossierIdAndPartageAvocatAndEstSupprimeFalseOrderByDateChargementDesc(
                DOSSIER, StatutPartageAvocat.PARTAGE)).thenReturn(List.of(piece()));

        var reponse = service.piecesDeMonDossier(DOSSIER);

        assertThat(reponse.constitue()).isTrue();
        assertThat(reponse.pieces()).extracting("nomFichier").containsExactly("assignation.pdf");
    }

    // ─────────────────────────────────────────────────────────────── l'ouverture

    @Test
    void piece_nonConstitue_403_sansTrace() {
        affecte(true);
        constitue(false);

        assertThatThrownBy(() -> service.pieceDeMonDossier(DOSSIER, 7L)).isInstanceOf(ApiException.class)
                .hasMessageContaining("constitution");
        verify(historiqueActionService, never()).enregistrer(anyLong(), any(), any());
    }

    /**
     * 404 et non 403 : répondre 403 confirmerait que la pièce existe, et l'avocat pourrait sonder la
     * GED de la banque en essayant des identifiants.
     */
    @Test
    @DisplayName("une pièce non partagée répond 404, sans rien révéler ni rien tracer")
    void piece_nonPartagee_404() {
        affecte(true);
        constitue(true);
        when(documentRepository.findByIdAndDossierIdAndPartageAvocatAndEstSupprimeFalse(
                8L, DOSSIER, StatutPartageAvocat.PARTAGE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pieceDeMonDossier(DOSSIER, 8L)).isInstanceOf(ApiException.class)
                .hasMessageContaining("introuvable");
        verify(minioStorageService, never()).urlTelechargement(any());
        verify(historiqueActionService, never()).enregistrer(anyLong(), any(), any());
    }

    /** La lecture est tracée : il faut pouvoir dire si l'avocat avait vu la pièce avant l'audience. */
    @Test
    @DisplayName("ouvrir une pièce partagée donne son URL et trace la lecture")
    void piece_ouverte_tracee() {
        affecte(true);
        constitue(true);
        when(documentRepository.findByIdAndDossierIdAndPartageAvocatAndEstSupprimeFalse(
                7L, DOSSIER, StatutPartageAvocat.PARTAGE)).thenReturn(Optional.of(piece()));
        when(minioStorageService.urlTelechargement("dossiers/3/uuid-assignation.pdf")).thenReturn("http://minio/url");

        var reponse = service.pieceDeMonDossier(DOSSIER, 7L);

        assertThat(reponse.urlTelechargement()).isEqualTo("http://minio/url");
        verify(historiqueActionService).enregistrer(DOSSIER, TypeActionHistorique.DOCUMENT_CONSULTE_PAR_AVOCAT,
                Map.of("documentId", 7L, "avocatId", AVOCAT));
    }

    // ─────────────────────────────────────────────────────────────── la note

    private void uneAudienceAnnotee() {
        Audience audience = Audience.builder().id(11L).datePlanifiee(LocalDate.now().plusDays(3))
                .statut(StatutAudience.PLANIFIEE).juridiction("TGI Douala")
                .compteRendu("Avocat peu préparé, à surveiller")
                .noteAvocat("Apportez l'original de l'acte de prêt").build();
        when(audienceRepository.findByDossierIdOrderByDatePlanifieeDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(audience)));
    }

    /** La note est une communication de la banque : c'est le mandat qui l'ouvre, pas l'affectation. */
    @Test
    @DisplayName("la note à l'avocat reste masquée tant qu'il n'est pas constitué")
    void note_masqueeSansConstitution() {
        affecte(true);
        constitue(false);
        uneAudienceAnnotee();

        assertThat(service.audiencesDeMonDossier(DOSSIER).get(0).noteAvocat()).isNull();
    }

    @Test
    @DisplayName("constitué, il lit la note - et jamais le compte rendu interne")
    void note_lueParAvocatConstitue() {
        affecte(true);
        constitue(true);
        uneAudienceAnnotee();

        var audience = service.audiencesDeMonDossier(DOSSIER).get(0);

        assertThat(audience.noteAvocat()).isEqualTo("Apportez l'original de l'acte de prêt");
        // RG-INT-07 : le compte rendu du juriste n'a aucun champ dans ce que l'avocat reçoit.
        assertThat(audience.toString()).doesNotContain("peu préparé");
    }
}
