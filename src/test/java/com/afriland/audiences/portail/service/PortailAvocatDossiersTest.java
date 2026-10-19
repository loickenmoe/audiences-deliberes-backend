package com.afriland.audiences.portail.service;

import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.ged.service.ValidationFichierService;
import com.afriland.audiences.intervenant.mapper.DemandeConstitutionMapper;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.publication.domain.AutrePublication;
import com.afriland.audiences.publication.domain.CorrespondanceAvocat;
import com.afriland.audiences.publication.domain.Publication;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.mapper.PublicationMapper;
import com.afriland.audiences.publication.repository.CorrespondanceAvocatRepository;
import com.afriland.audiences.publication.repository.PublicationRepository;
import com.afriland.audiences.publication.web.dto.PublicationResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The lawyer portal case list and its search.
 *
 * <p>The selector must not load only one page of the most recent cases: a lawyer with many cases
 * would silently lose the older ones (no fee request or report possible). A list that lies by
 * omission is worse than an error.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortailAvocatDossiersTest {

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
    @Mock private com.afriland.audiences.ged.repository.DocumentRepository documentRepository;
    @Mock private com.afriland.audiences.common.audit.HistoriqueActionService historiqueActionService;

    private static final Pageable PAGE = PageRequest.of(0, 100);

    private PortailAvocatService service;

    @BeforeEach
    void setUp() {
        service = new PortailAvocatService(currentUserService, compteAvocatService, dossierRepository,
                dossierAvocatRepository, audienceRepository, publicationRepository,
                correspondanceAvocatRepository, publicationMapper, demandeConstitutionRepository,
                demandeConstitutionMapper, minioStorageService, validationFichierService,
                documentRepository, historiqueActionService);
        Utilisateur compte = Utilisateur.builder().id(30L).nom("Kenmoe").prenom("Loïc").build();
        Avocat avocat = Avocat.builder().id(1L).nom("Loïc Kenmoe").compteKeycloak("avocat.test").build();
        when(currentUserService.getCurrentUser()).thenReturn(compte);
        when(compteAvocatService.avocatDe(compte)).thenReturn(Optional.of(avocat));
    }

    private Dossier dossier(Long id, String reference) {
        return Dossier.builder().id(id).reference(reference).nature("Saisie immobilière")
                .juridictionSaisie("Juridiction Yaoundé").build();
    }

    @Test
    @DisplayName("sans recherche, la liste entière est demandée")
    void mesDossiers_sansRecherche() {
        when(dossierRepository.findDossiersDeLAvocat(1L, PAGE))
                .thenReturn(new PageImpl<>(List.of(dossier(9L, "DOS-2026-009"))));

        var page = service.mesDossiers(null, PAGE);

        assertThat(page.content()).hasSize(1);
        verify(dossierRepository, never()).rechercherDossiersDeLAvocat(any(), any(), any());
    }

    /**
     * An old case, outside the loaded page, must be found by search. The server searches: filtering the
     * received page would only search the most recent hundred.
     */
    @Test
    @DisplayName("la recherche retrouve un dossier ancien, hors de la première page")
    void mesDossiers_rechercheParReference() {
        when(dossierRepository.rechercherDossiersDeLAvocat(1L, "dss1", PAGE))
                .thenReturn(new PageImpl<>(List.of(dossier(1L, "dss1"))));

        var page = service.mesDossiers("dss1", PAGE);

        assertThat(page.content()).extracting("reference").containsExactly("dss1");
        verify(dossierRepository, never()).findDossiersDeLAvocat(any(), any());
    }

    /** Input surrounded by spaces is the same search: it must not return nothing. */
    @Test
    void mesDossiers_rechercheEpargneeDesEspaces() {
        when(dossierRepository.rechercherDossiersDeLAvocat(eq(1L), eq("dss1"), any()))
                .thenReturn(new PageImpl<>(List.of(dossier(1L, "dss1"))));

        assertThat(service.mesDossiers("  dss1  ", PAGE).content()).hasSize(1);
    }

    /** An empty search is not a search: the whole list is wanted. */
    @Test
    void mesDossiers_rechercheVideVautListeEntiere() {
        when(dossierRepository.findDossiersDeLAvocat(1L, PAGE)).thenReturn(new PageImpl<>(List.of()));

        service.mesDossiers("   ", PAGE);

        verify(dossierRepository).findDossiersDeLAvocat(1L, PAGE);
        verify(dossierRepository, never()).rechercherDossiersDeLAvocat(any(), any(), any());
    }

    /**
     * The restriction stays in the query: the search receives the current lawyer's id, never another's.
     * This prevents an external provider from probing the bank's portfolio by guessing references.
     */
    @Test
    void mesDossiers_rechercheResteLimiteeALAvocatCourant() {
        when(dossierRepository.rechercherDossiersDeLAvocat(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.mesDossiers("DOS", PAGE);

        verify(dossierRepository).rechercherDossiersDeLAvocat(eq(1L), eq("DOS"), any());
    }

    /**
     * <b>The correspondence accompanies the lawyer's publication.</b> {@code mesPublications} must map
     * with the full mapper signature, otherwise the lawyer is notified of a correspondence that no screen
     * can show.
     */
    @Test
    @DisplayName("la publication de l'avocat porte la correspondance qui lui a été transmise")
    void mesPublications_porteLaCorrespondance() {
        Publication publication = publicationDeLAvocat(7L);
        when(publicationRepository.findByAvocatId(1L, PAGE))
                .thenReturn(new PageImpl<>(List.of(publication)));
        when(correspondanceAvocatRepository.findByPublicationIdIn(List.of(7L)))
                .thenReturn(List.of(correspondance(publication)));
        capturerLeMapper();

        var page = service.mesPublications(null, PAGE);

        assertThat(page.content()).hasSize(1);
        var transmise = page.content().get(0).correspondance();
        assertThat(transmise).isNotNull();
        assertThat(transmise.contenu()).isEqualTo("Merci de préciser la date de l'audience.");
        // A letter without a signature is not one: the lawyer must know whom they are answering.
        assertThat(transmise.auteurNom()).isEqualTo("Loïc Kenmoe");
    }

    /**
     * <b>Rule guarded here.</b> The legal officer never addresses the lawyer directly (US 5.5,
     * RG-INT-07): they comment for the DJ/DJA, who forwards <b>one</b> correspondence. Including comments
     * in the portal payload would expose internal deliberation to the person it discusses.
     */
    @Test
    @DisplayName("les commentaires internes ne partent jamais vers l'avocat")
    void mesPublications_tientLesCommentairesInternesAEcart() {
        Publication publication = publicationDeLAvocat(7L);
        when(publicationRepository.findByAvocatId(1L, PAGE))
                .thenReturn(new PageImpl<>(List.of(publication)));
        when(correspondanceAvocatRepository.findByPublicationIdIn(List.of(7L)))
                .thenReturn(List.of(correspondance(publication)));
        capturerLeMapper();

        assertThat(service.mesPublications(null, PAGE).content().get(0).commentaires()).isNull();
    }

    /**
     * A page without publications must not query the database: {@code findByPublicationIdIn} with an
     * empty list would produce a pointless {@code IN ()}.
     */
    @Test
    void mesPublications_pageVideNInterrogePas() {
        when(publicationRepository.findByAvocatId(1L, PAGE)).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.mesPublications(null, PAGE).content()).isEmpty();

        verify(correspondanceAvocatRepository, never()).findByPublicationIdIn(any());
    }

    /**
     * Ten publications, <b>one</b> read. The portal shows ten per page: one read per publication would
     * issue ten queries where one suffices.
     */
    @Test
    @DisplayName("une seule lecture des correspondances pour toute la page")
    void mesPublications_uneSeuleLecturePourLaPage() {
        List<Publication> publications = java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(id -> (Publication) publicationDeLAvocat(id))
                .toList();
        when(publicationRepository.findByAvocatId(1L, PAGE)).thenReturn(new PageImpl<>(publications));
        when(correspondanceAvocatRepository.findByPublicationIdIn(any())).thenReturn(List.of());
        capturerLeMapper();

        service.mesPublications(null, PAGE);

        verify(correspondanceAvocatRepository, times(1)).findByPublicationIdIn(any());
    }

    /** A status filter must not deprive the lawyer of their correspondence. */
    @Test
    void mesPublications_filtreeParStatutPorteAussiLaCorrespondance() {
        Publication publication = publicationDeLAvocat(7L);
        when(publicationRepository.findByAvocatIdAndStatutValidation(1L, StatutPublication.VALIDE, PAGE))
                .thenReturn(new PageImpl<>(List.of(publication)));
        when(correspondanceAvocatRepository.findByPublicationIdIn(List.of(7L)))
                .thenReturn(List.of(correspondance(publication)));
        capturerLeMapper();

        assertThat(service.mesPublications(StatutPublication.VALIDE, PAGE).content().get(0).correspondance())
                .isNotNull();
    }

    private AutrePublication publicationDeLAvocat(long id) {
        AutrePublication publication = new AutrePublication();
        publication.setId(id);
        return publication;
    }

    private CorrespondanceAvocat correspondance(Publication publication) {
        return CorrespondanceAvocat.builder()
                .id(50L)
                .publication(publication)
                .auteur(Utilisateur.builder().id(30L).nom("Kenmoe").prenom("Loïc").build())
                .contenu("Merci de préciser la date de l'audience.")
                .createdAt(LocalDateTime.of(2026, 10, 14, 9, 0))
                .build();
    }

    /**
     * The mapper is stubbed to return what the service passes, so the test covers <b>what the service
     * transmits</b>, exactly where the correspondence could be lost.
     */
    private void capturerLeMapper() {
        when(publicationMapper.toResponse(any(), any(), any(), any())).thenAnswer(invocation ->
                new PublicationResponse(invocation.<Publication>getArgument(0).getId(), null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null,
                        invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
    }
}
