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
import com.afriland.audiences.publication.mapper.PublicationMapper;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @Mock private PublicationMapper publicationMapper;
    @Mock private DemandeConstitutionRepository demandeConstitutionRepository;
    @Mock private DemandeConstitutionMapper demandeConstitutionMapper;
    @Mock private MinioStorageService minioStorageService;
    @Mock private ValidationFichierService validationFichierService;

    private static final Pageable PAGE = PageRequest.of(0, 100);

    private PortailAvocatService service;

    @BeforeEach
    void setUp() {
        service = new PortailAvocatService(currentUserService, compteAvocatService, dossierRepository,
                dossierAvocatRepository, audienceRepository, publicationRepository, publicationMapper,
                demandeConstitutionRepository, demandeConstitutionMapper, minioStorageService,
                validationFichierService);
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
}
