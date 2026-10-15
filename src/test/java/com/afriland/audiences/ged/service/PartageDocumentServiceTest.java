package com.afriland.audiences.ged.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.ged.domain.DecisionPartage;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.StatutPartageAvocat;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.web.dto.ArbitragePartageRequest;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le partage d'une pièce avec l'avocat du dossier (QF-64).
 *
 * <p><b>Les règles que ces tests gardent</b>, arbitrées par le porteur du projet le 2026-10-15 :
 * tout juriste partage sans validation ; sur un dossier sensible, le juriste propose et le DJ ou la
 * DJA valide ; un rejet exige son motif ; et chaque geste laisse sa trace - une pièce qui sort de la
 * banque doit pouvoir dire qui l'a laissée sortir.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartageDocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private com.afriland.audiences.dossier.repository.DossierAvocatRepository dossierAvocatRepository;
    @Mock private com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository demandeConstitutionRepository;

    private PartageDocumentService service;

    private final Utilisateur juriste = utilisateur(10L, "JURISTE");
    private final Utilisateur dj = utilisateur(20L, "DJ");

    @BeforeEach
    void setUp() {
        service = new PartageDocumentService(documentRepository, currentUserService, historiqueActionService,
                dossierAvocatRepository, demandeConstitutionRepository);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Utilisateur utilisateur(Long id, String profil) {
        return Utilisateur.builder().id(id).prenom("Prénom").nom(profil)
                .profil(Profil.builder().code(profil).build()).build();
    }

    private Document document(boolean sensible, StatutPartageAvocat statut) {
        Dossier dossier = Dossier.builder().id(3L).reference("DOS-2026-003").estSensible(sensible).build();
        Document document = Document.builder().id(7L).dossier(dossier).nomFichier("assignation.pdf")
                .typeDocument("Assignation").estSupprime(false).partageAvocat(statut).build();
        if (statut != StatutPartageAvocat.NON) {
            document.setPartagePar(juriste);
            document.setPartageLe(LocalDateTime.now().minusDays(1));
        }
        when(documentRepository.findById(7L)).thenReturn(Optional.of(document));
        return document;
    }

    // ─────────────────────────────────────────────────────────────── partager

    @Test
    @DisplayName("un juriste partage directement une pièce d'un dossier ordinaire")
    void partager_dossierOrdinaire() {
        Document document = document(false, StatutPartageAvocat.NON);
        when(currentUserService.getCurrentUser()).thenReturn(juriste);

        var reponse = service.partager(7L);

        assertThat(reponse.partageAvocat()).isEqualTo(StatutPartageAvocat.PARTAGE);
        assertThat(document.getPartagePar()).isEqualTo(juriste);
        // Aucune validation n'est en jeu hors d'un dossier sensible : rien à signer.
        assertThat(document.getPartageValidePar()).isNull();
        verify(historiqueActionService).enregistrer(3L, TypeActionHistorique.DOCUMENT_PARTAGE_AVOCAT,
                Map.of("documentId", 7L));
    }

    /**
     * <b>La règle du dossier sensible.</b> Le geste du juriste ne rend rien visible : il propose, et
     * c'est le DJ ou la DJA qui décide. Si ce test tombait, une pièce d'un dossier sensible partirait
     * chez l'avocat sans que la direction l'ait vue.
     */
    @Test
    @DisplayName("sur un dossier sensible, le juriste propose : rien n'est visible avant le DJ/DJA")
    void partager_dossierSensible_parJuriste() {
        Document document = document(true, StatutPartageAvocat.NON);
        when(currentUserService.getCurrentUser()).thenReturn(juriste);

        var reponse = service.partager(7L);

        assertThat(reponse.partageAvocat()).isEqualTo(StatutPartageAvocat.PROPOSE);
        assertThat(document.getPartageValidePar()).isNull();
        verify(historiqueActionService).enregistrer(3L, TypeActionHistorique.DOCUMENT_PARTAGE_PROPOSE,
                Map.of("documentId", 7L));
    }

    /** Le DJ qui partage lui-même n'a pas à se valider : sa décision est la validation. */
    @Test
    void partager_dossierSensible_parDj() {
        Document document = document(true, StatutPartageAvocat.NON);
        when(currentUserService.getCurrentUser()).thenReturn(dj);

        var reponse = service.partager(7L);

        assertThat(reponse.partageAvocat()).isEqualTo(StatutPartageAvocat.PARTAGE);
        assertThat(document.getPartageValidePar()).isEqualTo(dj);
        assertThat(document.getPartageValideLe()).isNotNull();
    }

    /**
     * <b>QF-65 : le SH et l'Assistante partagent</b>, « ils sont à la base des juristes ». Sur un
     * dossier ordinaire leur geste partage ; sur un dossier sensible, il propose, comme celui du
     * juriste - seule la direction partage directement.
     */
    @Test
    @DisplayName("le SH et l'Assistante partagent un dossier ordinaire, et proposent un dossier sensible")
    void partager_shEtAssistante() {
        for (String profil : java.util.List.of("SH", "ASSISTANTE")) {
            when(currentUserService.getCurrentUser()).thenReturn(utilisateur(30L, profil));

            Document ordinaire = document(false, StatutPartageAvocat.NON);
            assertThat(service.partager(7L).partageAvocat()).as(profil).isEqualTo(StatutPartageAvocat.PARTAGE);
            assertThat(ordinaire.getPartageValidePar()).isNull();

            document(true, StatutPartageAvocat.NON);
            assertThat(service.partager(7L).partageAvocat()).as(profil).isEqualTo(StatutPartageAvocat.PROPOSE);
        }
    }

    // ─────────────────────────────────────────────────────────────── l'etat

    /** La règle même qui ouvre les pièces : un avocat affecté ET constitué. */
    @Test
    @DisplayName("l'état dit si un avocat affecté est constitué")
    void etat_constitution() {
        var avocat = com.afriland.audiences.referentiel.domain.Avocat.builder().id(1L).build();
        var affectation = new com.afriland.audiences.dossier.domain.DossierAvocat();
        affectation.setAvocat(avocat);
        when(dossierAvocatRepository.findByDossierId(3L)).thenReturn(java.util.List.of(affectation));

        when(demandeConstitutionRepository.existsByDossierIdAndPrestataireIdAndStatut(3L, 1L,
                com.afriland.audiences.intervenant.domain.StatutConstitution.VALIDEE)).thenReturn(false);
        assertThat(service.etat(3L).avocatConstitue()).isFalse();

        when(demandeConstitutionRepository.existsByDossierIdAndPrestataireIdAndStatut(3L, 1L,
                com.afriland.audiences.intervenant.domain.StatutConstitution.VALIDEE)).thenReturn(true);
        assertThat(service.etat(3L).avocatConstitue()).isTrue();
    }

    /** Un dossier sans avocat affecté n'en a aucun de constitué. */
    @Test
    void etat_sansAvocat() {
        when(dossierAvocatRepository.findByDossierId(3L)).thenReturn(java.util.List.of());
        assertThat(service.etat(3L).avocatConstitue()).isFalse();
    }

    @Test
    void partager_dejaPartage_409() {
        document(false, StatutPartageAvocat.PARTAGE);
        when(currentUserService.getCurrentUser()).thenReturn(juriste);

        assertThatThrownBy(() -> service.partager(7L)).isInstanceOf(ApiException.class);
        verify(historiqueActionService, never()).enregistrer(anyLong(), any(), any());
    }

    /** Une pièce supprimée n'existe plus pour personne hors de l'audit : elle ne se partage pas. */
    @Test
    void partager_documentSupprime_404() {
        Document document = document(false, StatutPartageAvocat.NON);
        document.setEstSupprime(true);

        assertThatThrownBy(() -> service.partager(7L)).isInstanceOf(ApiException.class)
                .hasMessageContaining("introuvable");
    }

    // ─────────────────────────────────────────────────────────────── retirer

    @Test
    @DisplayName("retirer un partage efface tout ce qui le décrivait, et le trace")
    void retirer_partage() {
        Document document = document(true, StatutPartageAvocat.PARTAGE);
        document.setPartageValidePar(dj);
        document.setPartageValideLe(LocalDateTime.now());

        var reponse = service.retirer(7L);

        assertThat(reponse.partageAvocat()).isEqualTo(StatutPartageAvocat.NON);
        // La contrainte de la base l'exige : un état NON ne garde ni auteur ni validation.
        assertThat(document.getPartagePar()).isNull();
        assertThat(document.getPartageLe()).isNull();
        assertThat(document.getPartageValidePar()).isNull();
        assertThat(document.getPartageValideLe()).isNull();
        verify(historiqueActionService).enregistrer(3L, TypeActionHistorique.DOCUMENT_PARTAGE_RETIRE,
                Map.of("documentId", 7L, "ancienStatut", "PARTAGE"));
    }

    @Test
    void retirer_nonPartage_409() {
        document(false, StatutPartageAvocat.NON);

        assertThatThrownBy(() -> service.retirer(7L)).isInstanceOf(ApiException.class);
    }

    // ─────────────────────────────────────────────────────────────── arbitrer

    @Test
    @DisplayName("le DJ valide une proposition : la pièce devient visible, signée de sa main")
    void arbitrer_valide() {
        Document document = document(true, StatutPartageAvocat.PROPOSE);
        when(currentUserService.getCurrentUser()).thenReturn(dj);

        var reponse = service.arbitrer(7L, new ArbitragePartageRequest(DecisionPartage.VALIDE, null));

        assertThat(reponse.partageAvocat()).isEqualTo(StatutPartageAvocat.PARTAGE);
        assertThat(document.getPartageValidePar()).isEqualTo(dj);
        // Le proposant reste nommé : la validation s'ajoute, elle ne remplace personne.
        assertThat(document.getPartagePar()).isEqualTo(juriste);
    }

    /** Le juriste qui a proposé doit savoir pourquoi la pièce ne part pas. */
    @Test
    @DisplayName("un rejet sans motif est refusé, et la proposition reste intacte")
    void arbitrer_rejetSansMotif_400() {
        Document document = document(true, StatutPartageAvocat.PROPOSE);
        when(currentUserService.getCurrentUser()).thenReturn(dj);

        assertThatThrownBy(() -> service.arbitrer(7L, new ArbitragePartageRequest(DecisionPartage.REJETE, "  ")))
                .isInstanceOf(ApiException.class).hasMessageContaining("motif");
        assertThat(document.getPartageAvocat()).isEqualTo(StatutPartageAvocat.PROPOSE);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void arbitrer_rejetMotive() {
        Document document = document(true, StatutPartageAvocat.PROPOSE);
        when(currentUserService.getCurrentUser()).thenReturn(dj);

        var reponse = service.arbitrer(7L,
                new ArbitragePartageRequest(DecisionPartage.REJETE, "Pièce couverte par le secret bancaire"));

        assertThat(reponse.partageAvocat()).isEqualTo(StatutPartageAvocat.NON);
        assertThat(document.getPartagePar()).isNull();
        verify(historiqueActionService).enregistrer(eq(3L), eq(TypeActionHistorique.DOCUMENT_PARTAGE_ARBITRE),
                eq(Map.of("documentId", 7L, "decision", "REJETE", "motif", "Pièce couverte par le secret bancaire")));
    }

    /** Une pièce déjà partagée n'a plus rien à valider : la redemander serait une erreur de l'écran. */
    @Test
    void arbitrer_horsProposition_409() {
        document(false, StatutPartageAvocat.PARTAGE);

        assertThatThrownBy(() -> service.arbitrer(7L, new ArbitragePartageRequest(DecisionPartage.VALIDE, null)))
                .isInstanceOf(ApiException.class);
    }
}
