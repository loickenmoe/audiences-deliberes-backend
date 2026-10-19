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
 * A person's activity sheet.
 *
 * <p>What is tested is the <b>scope</b>, not arithmetic. This report names a person and states what
 * they did: serving it to someone not allowed to read it is the only serious defect possible, and the
 * service enforces it, not the controller annotation.
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
        // Two rows, not forty-nine: an activity sheet is not an inventory of what the person did not do.
        assertThat(rapport.lignes()).hasSize(2);
        // Most frequent first, for readability: a single person is ranked against nobody.
        assertThat(rapport.lignes().get(0).code()).isEqualTo(TypeActionHistorique.AUDIENCE_PLANIFIEE);
        assertThat(rapport.lignes().get(0).total()).isEqualTo(9);
    }

    /**
     * The report must faithfully reflect what the user did. The management comparison table covers six
     * natures; this sheet must carry any of them, otherwise the Assistant and the SH would be empty.
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
     * Defined <b>by exclusion</b>, the list of counted natures picks up a new code by itself. A closed
     * list goes silent when someone forgets to update it, and only in production.
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
        // No delay to look for without any action: one less native query, and a result that could say
        // nothing anyway.
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
     * The counterpart: the right is read from the <b>profile</b>, never the role. {@code ROLE_JURISTE} is
     * composite, so a scope based on it would expose everyone's activity to everyone.
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
     * The detail is the gateway to cases. Without the sheet's scope, knowing an identifier would be
     * enough to read what the count protects.
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
        // The refusal happens before any read: no need to know who asks to say no.
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
     * A lawyer does have activity to read: they upload fee requests, hearing reports and other
     * publications, and answer explanation requests. All four are logged under their own account.
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
         * The average entry delay stays null for them, by design: the lawyer **uploads** a report
         * (PUBLICATION_CR_DEPOSE), the legal officer **records** it on the hearing
         * (AUDIENCE_COMPTE_RENDU_ENREGISTRE). Measuring the latter under the lawyer's name would attribute a
         * delay that is not theirs.
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
     * The sensitive point. A lawyer knows directory identifiers (they appear on their own screens). If
     * `avocatId` opened a second door, changing the number would reveal a colleague's activity. The
     * parameter is therefore translated into an account, then subject to the **same** rule.
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
         * The colleague's account is **not** read. The order guarantees it: reading it first would let the
         * refusal reveal whether that colleague had ever logged in.
         */
        verify(compteAvocatService, never()).utilisateurDe(any());
    }

    /** Requesting through one's own directory entry is allowed: it is one's own activity. */
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
     * A lawyer listed in the directory but never logged in has no account, hence no history. Saying so
     * distinguishes "did nothing" from "never came", instead of returning an empty sheet.
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
        // The refusal happens before any read: no need to know who asks to say no.
        verify(currentUserService, never()).getCurrentUser();
    }
}
