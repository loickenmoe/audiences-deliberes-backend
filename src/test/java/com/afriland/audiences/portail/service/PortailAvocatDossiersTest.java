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
 * La liste des dossiers du portail avocat, et sa recherche (2026-09-28).
 *
 * <p><b>Le défaut que ces tests protègent.</b> Le sélecteur chargeait une page des dossiers les
 * plus récents. Un avocat qui en suit davantage — 427 dans la base de recette — ne voyait plus les
 * anciens : ni demande de frais, ni compte rendu possible pour eux. Le dossier existait,
 * l'affectation aussi ; seule la liste était tronquée, <b>en silence</b>. Une liste qui ment par
 * omission est pire qu'une erreur : rien n'avertit qu'il manque quelque chose.
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
     * Le cas signalé en recette : un dossier ancien, hors de la page chargée, que la recherche doit
     * retrouver. C'est le serveur qui cherche — filtrer la page reçue n'aurait cherché que parmi
     * les cent plus récents, et aurait répondu « aucun résultat » pour un dossier bien présent.
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

    /** Une saisie entourée d'espaces est la même recherche : elle ne doit pas rester sans résultat. */
    @Test
    void mesDossiers_rechercheEpargneeDesEspaces() {
        when(dossierRepository.rechercherDossiersDeLAvocat(eq(1L), eq("dss1"), any()))
                .thenReturn(new PageImpl<>(List.of(dossier(1L, "dss1"))));

        assertThat(service.mesDossiers("  dss1  ", PAGE).content()).hasSize(1);
    }

    /** Une recherche vide n'est pas une recherche : c'est la liste entière qu'on veut. */
    @Test
    void mesDossiers_rechercheVideVautListeEntiere() {
        when(dossierRepository.findDossiersDeLAvocat(1L, PAGE)).thenReturn(new PageImpl<>(List.of()));

        service.mesDossiers("   ", PAGE);

        verify(dossierRepository).findDossiersDeLAvocat(1L, PAGE);
        verify(dossierRepository, never()).rechercherDossiersDeLAvocat(any(), any(), any());
    }

    /**
     * La restriction reste portée par la requête : la recherche reçoit l'identifiant de l'avocat
     * courant, jamais celui d'un autre. C'est ce qui empêche un prestataire externe de sonder le
     * portefeuille de la banque par tâtonnement sur les références.
     */
    @Test
    void mesDossiers_rechercheResteLimiteeALAvocatCourant() {
        when(dossierRepository.rechercherDossiersDeLAvocat(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.mesDossiers("DOS", PAGE);

        verify(dossierRepository).rechercherDossiersDeLAvocat(eq(1L), eq("DOS"), any());
    }

    // ------------------------------------------------------------------ QF-58

    /**
     * <b>La correspondance accompagne la publication de l'avocat</b> (QF-58).
     *
     * <p>Le défaut, exactement : {@code mesPublications} cartographiait avec la forme à un seul
     * argument du mapper, qui délègue à {@code toResponse(publication, null, null, null)}. L'avocat
     * recevait donc la notification d'une correspondance transmise - {@code alerteService} et
     * {@code notificationPrestataireService} la lui envoient tous deux - sans qu'aucun écran ne
     * puisse la lui montrer. Signalé en recette le 2026-10-14.
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
        // Une lettre sans signature n'en est pas une : l'avocat doit savoir à qui il répond.
        assertThat(transmise.auteurNom()).isEqualTo("Loïc Kenmoe");
    }

    /**
     * <b>La règle que ce test garde.</b> Le juriste ne s'adresse jamais directement à l'avocat
     * (Q-20, US 5.5, RG-INT-07) : il commente pour le DJ/DJA, qui transmet <b>une</b> correspondance.
     * Joindre les commentaires à la charge utile du portail ouvrirait la délibération interne à
     * celui dont elle discute - et la correction de QF-58 passait à un argument près de le faire.
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
     * Une page sans publication ne doit pas interroger la base : {@code findByPublicationIdIn} avec
     * une liste vide produirait un {@code IN ()} pour rien, sur l'écran que l'avocat ouvre en
     * premier.
     */
    @Test
    void mesPublications_pageVideNInterrogePas() {
        when(publicationRepository.findByAvocatId(1L, PAGE)).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.mesPublications(null, PAGE).content()).isEmpty();

        verify(correspondanceAvocatRepository, never()).findByPublicationIdIn(any());
    }

    /**
     * Dix publications, <b>une</b> lecture. Le portail en affiche dix par page : une lecture par
     * publication ferait dix requêtes là où une suffit.
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

    /** Un filtre sur le statut ne doit pas priver l'avocat de sa correspondance. */
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
     * Le mapper est simulé : on lui fait rendre ce que le service lui passe, pour que le test porte
     * sur <b>ce que le service transmet</b> - le point même où la correspondance était perdue.
     */
    private void capturerLeMapper() {
        when(publicationMapper.toResponse(any(), any(), any(), any())).thenAnswer(invocation ->
                new PublicationResponse(invocation.<Publication>getArgument(0).getId(), null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null,
                        invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
    }
}
