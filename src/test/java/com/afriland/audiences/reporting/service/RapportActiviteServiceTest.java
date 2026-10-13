package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fiche d'activité d'une personne (Q-133).
 *
 * <p>Ce qui est éprouvé ici n'est pas l'arithmétique - c'est le <b>périmètre</b>. Ce rapport nomme
 * une personne et dit ce qu'elle a fait : le servir à qui ne doit pas le lire est le seul défaut
 * grave que ce code puisse avoir, et c'est le service qui le tient, pas l'annotation du contrôleur.
 */
@ExtendWith(MockitoExtension.class)
class RapportActiviteServiceTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);

    @Mock private HistoriqueActionRepository historiqueActionRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private CompteAvocatService compteAvocatService;
    @Mock private AvocatRepository avocatRepository;

    @InjectMocks private RapportActiviteService service;

    private static HistoriqueActionRepository.ActiviteParCode activite(TypeActionHistorique code, long total) {
        return new HistoriqueActionRepository.ActiviteParCode() {
            @Override public TypeActionHistorique getCode() { return code; }
            @Override public long getTotal() { return total; }
        };
    }

    private static Utilisateur personne(Long id, String prenom, String nom, String codeProfil) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(id);
        utilisateur.setPrenom(prenom);
        utilisateur.setNom(nom);
        if (codeProfil != null) {
            Profil profil = new Profil();
            profil.setCode(codeProfil);
            utilisateur.setProfil(profil);
        }
        return utilisateur;
    }

    @Test
    @DisplayName("compte toutes les natures accomplies, et aucune de celles qui ne l'ont pas été")
    void compteCeQuiAEteFait() {
        when(currentUserService.getCurrentUser()).thenReturn(personne(7L, "Awa", "Ndiaye", "JURISTE"));
        when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                .thenReturn(List.of(
                        activite(TypeActionHistorique.DOSSIER_CREE, 4),
                        activite(TypeActionHistorique.AUDIENCE_PLANIFIEE, 9)));
        when(historiqueActionRepository.delaiMoyenDeSaisieDe(anyLong(), any(), any())).thenReturn(2.5);

        var rapport = service.produire(null, null, DEBUT, FIN);

        assertThat(rapport.nom()).isEqualTo("Awa Ndiaye");
        assertThat(rapport.profil()).isEqualTo("JURISTE");
        assertThat(rapport.delaiMoyenSaisieCompteRendu()).isEqualTo(2.5);
        // Deux lignes, pas quarante-neuf : une fiche d'activité n'est pas l'inventaire de ce que la
        // personne n'a pas fait.
        assertThat(rapport.lignes()).hasSize(2);
        // Le plus fait d'abord, pour la lecture - une personne seule ne se classe contre personne.
        assertThat(rapport.lignes().get(0).code()).isEqualTo(TypeActionHistorique.AUDIENCE_PLANIFIEE);
        assertThat(rapport.lignes().get(0).total()).isEqualTo(9);
    }

    /**
     * Le cœur de la demande du métier du 2026-10-09 : « ce rapport devrait refléter fidèlement ce que
     * l'utilisateur a effectué ». Le tableau comparatif de la direction tient sur six natures ; cette
     * fiche doit pouvoir en porter n'importe laquelle, sinon l'Assistante et le SH y seraient vides.
     */
    @Test
    @DisplayName("interroge toutes les natures d'action, pas seulement les six du tableau de la direction")
    void refleteToutesLesNatures() {
        when(currentUserService.getCurrentUser()).thenReturn(personne(3L, "Ada", "Mbarga", "ASSISTANTE"));
        when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                .thenReturn(List.of(activite(TypeActionHistorique.LETTRE_CONSTITUTION_REDIGEE, 5)));
        lenient().when(historiqueActionRepository.delaiMoyenDeSaisieDe(anyLong(), any(), any())).thenReturn(null);

        service.produire(null, null, DEBUT, FIN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<TypeActionHistorique>> codes = ArgumentCaptor.forClass(Collection.class);
        verify(historiqueActionRepository).compterActivitesDe(any(), codes.capture(), any(), any());
        assertThat(codes.getValue())
                .hasSize(TypeActionHistorique.values().length - RapportActiviteService.CODES_EXCLUS.size())
                .contains(TypeActionHistorique.CONSTITUTION_ARBITREE,
                        TypeActionHistorique.SENSIBILITE_ARBITREE,
                        TypeActionHistorique.DOCUMENT_SUPPRESSION_ARBITREE)
                .doesNotContain(TypeActionHistorique.AUDIT_CONSULTE,
                        TypeActionHistorique.ARCHIVE_TELECHARGEE,
                        TypeActionHistorique.ETAPE_OUVERTE_AUTOMATIQUEMENT,
                        TypeActionHistorique.NON_CLASSEE);
    }

    /**
     * Prise <b>en creux</b>, la liste des natures comptées accueille d'elle-même un code nouveau.
     * C'est la réponse directe à Q-131 : un référentiel fermé se tait quand on oublie de l'alimenter,
     * et il ne se tait qu'en production.
     */
    @Test
    @DisplayName("un code nouveau entre dans la fiche sans qu'on l'y inscrive")
    void laListeEstPriseEnCreux() {
        assertThat(RapportActiviteService.CODES_DACTIVITE)
                .containsAll(List.of(TypeActionHistorique.EXPLICATION_DEMANDEE,
                        TypeActionHistorique.EXPLICATION_REPONDUE))
                .doesNotContainAnyElementsOf(RapportActiviteService.CODES_EXCLUS);
        assertThat(RapportActiviteService.CODES_DACTIVITE.size() + RapportActiviteService.CODES_EXCLUS.size())
                .isEqualTo(TypeActionHistorique.values().length);
    }

    @Test
    @DisplayName("sans compte rendu saisi, le délai moyen est absent - jamais zéro")
    void delaiAbsentPlutotQueZero() {
        when(currentUserService.getCurrentUser()).thenReturn(personne(7L, "Awa", "Ndiaye", "JURISTE"));
        when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                .thenReturn(List.of(activite(TypeActionHistorique.DOSSIER_CREE, 2)));
        when(historiqueActionRepository.delaiMoyenDeSaisieDe(anyLong(), any(), any())).thenReturn(null);

        assertThat(service.produire(null, null, DEBUT, FIN).delaiMoyenSaisieCompteRendu()).isNull();
    }

    @Test
    @DisplayName("une période sans activité est vide, et n'interroge même pas le délai")
    void periodeSansActivite() {
        when(currentUserService.getCurrentUser()).thenReturn(personne(7L, "Awa", "Ndiaye", "JURISTE"));
        when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                .thenReturn(List.of());

        var rapport = service.produire(null, null, DEBUT, FIN);

        assertThat(rapport.estVide()).isTrue();
        // Pas de délai à chercher sans la moindre action : une requête native de moins, et un
        // résultat qui ne pourrait de toute façon rien dire.
        verify(historiqueActionRepository, never()).delaiMoyenDeSaisieDe(anyLong(), any(), any());
    }

    @Test
    @DisplayName("la borne haute inclut toute la journée de fin")
    void borneHauteInclusive() {
        when(currentUserService.getCurrentUser()).thenReturn(personne(7L, "Awa", "Ndiaye", "JURISTE"));
        when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                .thenReturn(List.of());

        service.produire(null, null, DEBUT, FIN);

        ArgumentCaptor<LocalDateTime> haute = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(historiqueActionRepository)
                .compterActivitesDe(any(), anyCollection(), any(), haute.capture());
        assertThat(haute.getValue()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay());
    }

    @Test
    @DisplayName("un juriste ne lit pas l'activité d'un collègue")
    void unJuristeNeLitQueLaSienne() {
        when(currentUserService.getCurrentUser()).thenReturn(personne(7L, "Awa", "Ndiaye", "JURISTE"));

        assertThatThrownBy(() -> service.produire(9L, null, DEBUT, FIN))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("votre propre activité");
        verify(historiqueActionRepository, never()).compterActivitesDe(any(), anyCollection(), any(), any());
    }

    /**
     * Le pendant du test précédent : le droit se lit sur le <b>profil</b>, jamais sur le rôle.
     * {@code ROLE_JURISTE} est composite depuis Q-81 - un périmètre adossé à lui aurait donné
     * l'activité de chacun à tout le monde.
     */
    @Test
    @DisplayName("le DJ lit l'activité d'un collègue ; la DJA aussi")
    void laDirectionLitCelleDesAutres() {
        for (String profil : List.of("DJ", "DJA")) {
            when(currentUserService.getCurrentUser()).thenReturn(personne(1L, "Jean", "Directeur", profil));
            when(utilisateurRepository.findById(9L))
                    .thenReturn(Optional.of(personne(9L, "Awa", "Ndiaye", "JURISTE")));
            when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                    .thenReturn(List.of(activite(TypeActionHistorique.DOSSIER_CREE, 1)));
            lenient().when(historiqueActionRepository.delaiMoyenDeSaisieDe(anyLong(), any(), any())).thenReturn(null);

            var rapport = service.produire(9L, null, DEBUT, FIN);

            assertThat(rapport.utilisateurId()).isEqualTo(9L);
            assertThat(rapport.nom()).isEqualTo("Awa Ndiaye");
        }
    }

    @Test
    @DisplayName("se demander par son propre identifiant est permis, à tous")
    void seDemanderSoiMemeParIdentifiant() {
        when(currentUserService.getCurrentUser()).thenReturn(personne(7L, "Awa", "Ndiaye", "JURISTE"));
        when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                .thenReturn(List.of(activite(TypeActionHistorique.DOSSIER_CREE, 1)));
        when(historiqueActionRepository.delaiMoyenDeSaisieDe(anyLong(), any(), any())).thenReturn(null);

        assertThat(service.produire(7L, null, DEBUT, FIN).utilisateurId()).isEqualTo(7L);
    }

    /**
     * Le détail est la porte vers les dossiers. S'il n'appliquait pas le périmètre de la fiche, il
     * suffirait de connaître un identifiant pour lire ce que le décompte protège.
     */
    @Test
    @DisplayName("le détail applique le même périmètre que la fiche")
    void leDetailEstTenuParLeMemePerimetre() {
        when(currentUserService.getCurrentUser()).thenReturn(personne(7L, "Awa", "Ndiaye", "JURISTE"));

        assertThatThrownBy(() -> service.detailler(9L, null, TypeActionHistorique.DOSSIER_CREE, DEBUT, FIN,
                org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("votre propre activité");
    }

    @Test
    @DisplayName("on ne détaille pas une nature que la fiche ne compte pas")
    void pasDeDetailSurUneNatureExclue() {
        assertThatThrownBy(() -> service.detailler(null, null, TypeActionHistorique.AUDIT_CONSULTE, DEBUT, FIN,
                org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("n'est pas comptée");
        // Le refus tombe avant toute lecture : inutile de savoir qui demande pour répondre non.
        verify(currentUserService, never()).getCurrentUser();
    }

    @Test
    @DisplayName("un compte non rattaché au référentiel ne lit aucune activité")
    void compteNonRattache() {
        when(currentUserService.getCurrentUser()).thenReturn(null);

        assertThatThrownBy(() -> service.produire(null, null, DEBUT, FIN))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("rattaché");
    }

    /**
     * **Le test qui corrige une affirmation fausse.** J'ai soutenu, en livrant la fiche d'activite,
     * qu'un avocat n'aurait rien a y lire - « aucune des natures comptees n'est la sienne, sa fiche
     * serait vide par construction ». C'etait faux : il depose ses demandes de frais, ses comptes
     * rendus d'audience, ses autres publications, et repond aux demandes d'explication. Les quatre
     * sont journalisees sous son propre compte.
     */
    @Test
    @DisplayName("un avocat lit sa propre activité, qui n'est pas vide")
    void unAvocatLitLaSienne() {
        when(currentUserService.getCurrentUser()).thenReturn(personne(21L, "Me", "Fotso", "AVOCAT"));
        when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                .thenReturn(List.of(
                        activite(TypeActionHistorique.PUBLICATION_CR_DEPOSE, 6),
                        activite(TypeActionHistorique.FRAIS_DEMANDE_DEPOSEE, 3),
                        activite(TypeActionHistorique.EXPLICATION_REPONDUE, 1)));
        when(historiqueActionRepository.delaiMoyenDeSaisieDe(anyLong(), any(), any())).thenReturn(null);

        var rapport = service.produire(null, null, DEBUT, FIN);

        assertThat(rapport.utilisateurId()).isEqualTo(21L);
        assertThat(rapport.lignes()).hasSize(3);
        /*
         * Le delai moyen de saisie reste nul pour lui, et ce n'est pas un oubli : il **depose** un
         * compte rendu (PUBLICATION_CR_DEPOSE), c'est le juriste qui l'**enregistre** sur l'audience
         * (AUDIENCE_COMPTE_RENDU_ENREGISTRE). Mesurer le second sous le nom du premier lui
         * imputerait un delai qui n'est pas le sien.
         */
        assertThat(rapport.delaiMoyenSaisieCompteRendu()).isNull();
    }

    @Test
    @DisplayName("la direction lit l'activité d'un avocat par son inscription au répertoire")
    void laDirectionLitCelleDunAvocat() {
        Avocat avocat = new Avocat();
        avocat.setId(5L);
        when(currentUserService.getCurrentUser()).thenReturn(personne(1L, "Jean", "Directeur", "DJ"));
        when(avocatRepository.findById(5L)).thenReturn(Optional.of(avocat));
        when(compteAvocatService.utilisateurDe(avocat))
                .thenReturn(Optional.of(personne(21L, "Me", "Fotso", "AVOCAT")));
        when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                .thenReturn(List.of(activite(TypeActionHistorique.PUBLICATION_CR_DEPOSE, 2)));
        lenient().when(historiqueActionRepository.delaiMoyenDeSaisieDe(anyLong(), any(), any())).thenReturn(null);

        var rapport = service.produire(null, 5L, DEBUT, FIN);

        assertThat(rapport.utilisateurId()).isEqualTo(21L);
        assertThat(rapport.nom()).isEqualTo("Me Fotso");
    }

    /**
     * Le point sensible de cette ouverture. Un avocat connait les identifiants du repertoire - ils
     * circulent dans ses propres ecrans. Si `avocatId` ouvrait une seconde porte, il suffirait d'en
     * changer le chiffre pour lire l'activite d'un confrere. Le parametre est donc traduit en compte,
     * puis soumis a la **meme** regle que tout le reste.
     */
    @Test
    @DisplayName("un avocat ne lit pas l'activité d'un confrère, même par son inscription")
    void unAvocatNeLitPasCelleDunConfrere() {
        Avocat confrere = new Avocat();
        confrere.setId(7L);
        when(currentUserService.getCurrentUser()).thenReturn(personne(21L, "Me", "Fotso", "AVOCAT"));
        when(avocatRepository.findById(7L)).thenReturn(Optional.of(confrere));
        when(compteAvocatService.estLeCompteDe(any(), eq(confrere))).thenReturn(false);

        assertThatThrownBy(() -> service.produire(null, 7L, DEBUT, FIN))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("votre propre activité");
        verify(historiqueActionRepository, never()).compterActivitesDe(any(), anyCollection(), any(), any());
        /*
         * Le compte du confrère n'est **pas** lu. C'est l'ordre qui le garantit, et il importe : le
         * lire d'abord aurait fait dire au refus, par sa nature, si ce confrère s'était déjà
         * connecté - une information que personne n'a demandée et qui n'est pas la sienne.
         */
        verify(compteAvocatService, never()).utilisateurDe(any());
    }

    /** Se demander par sa propre inscription est permis : c'est bien sa propre activité. */
    @Test
    @DisplayName("un avocat se demande par sa propre inscription")
    void unAvocatSeDemandeParSonInscription() {
        Avocat lui = new Avocat();
        lui.setId(5L);
        when(currentUserService.getCurrentUser()).thenReturn(personne(21L, "Me", "Fotso", "AVOCAT"));
        when(avocatRepository.findById(5L)).thenReturn(Optional.of(lui));
        when(compteAvocatService.estLeCompteDe(any(), eq(lui))).thenReturn(true);
        when(compteAvocatService.utilisateurDe(lui))
                .thenReturn(Optional.of(personne(21L, "Me", "Fotso", "AVOCAT")));
        when(historiqueActionRepository.compterActivitesDe(any(), anyCollection(), any(), any()))
                .thenReturn(List.of(activite(TypeActionHistorique.PUBLICATION_CR_DEPOSE, 1)));
        when(historiqueActionRepository.delaiMoyenDeSaisieDe(anyLong(), any(), any())).thenReturn(null);

        assertThat(service.produire(null, 5L, DEBUT, FIN).utilisateurId()).isEqualTo(21L);
    }

    /**
     * Un avocat inscrit au repertoire mais jamais connecte n'a pas de compte, donc pas une seule
     * ligne d'historique. Rendre une fiche vide laisserait croire qu'il n'a rien fait ; le dire
     * distingue « rien fait » de « jamais venu ». Meme distinction qu'en QF-45.
     */
    @Test
    @DisplayName("un avocat jamais connecté est nommé comme tel, pas rendu vide")
    void unAvocatJamaisConnecte() {
        Avocat avocat = new Avocat();
        avocat.setId(9L);
        when(currentUserService.getCurrentUser()).thenReturn(personne(1L, "Jean", "Directeur", "DJ"));
        when(avocatRepository.findById(9L)).thenReturn(Optional.of(avocat));
        when(compteAvocatService.utilisateurDe(avocat)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.produire(null, 9L, DEBUT, FIN))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("jamais connect");
    }

    @Test
    @DisplayName("les deux désignations à la fois sont refusées, plutôt qu'une des deux ignorée")
    void pasLesDeuxDesignations() {
        assertThatThrownBy(() -> service.produire(3L, 5L, DEBUT, FIN))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("pas les deux");
        // Le refus tombe avant toute lecture : inutile de savoir qui demande pour repondre non.
        verify(currentUserService, never()).getCurrentUser();
    }
}
