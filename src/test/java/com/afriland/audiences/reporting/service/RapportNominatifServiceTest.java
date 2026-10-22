package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each person's activity over a period (N6).
 *
 * <p>Checks that the report **counts what was done**, invents nothing where history is silent, and
 * resists the two temptations of a named report: ranking, and adding up actions that do not add up.
 */
@ExtendWith(MockitoExtension.class)
class RapportNominatifServiceTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);

    @Mock private HistoriqueActionRepository historiqueActionRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private AvocatRepository avocatRepository;

    @InjectMocks private RapportNominatifService service;

    private static HistoriqueActionRepository.ActiviteParPersonne activite(
            Long utilisateurId, TypeActionHistorique code, long total) {
        return new HistoriqueActionRepository.ActiviteParPersonne() {
            @Override public Long getUtilisateurId() { return utilisateurId; }
            @Override public TypeActionHistorique getCode() { return code; }
            @Override public long getTotal() { return total; }
        };
    }

    private static HistoriqueActionRepository.DelaiSaisieParPersonne delai(Long utilisateurId, Double jours) {
        return new HistoriqueActionRepository.DelaiSaisieParPersonne() {
            @Override public Long getUtilisateurId() { return utilisateurId; }
            @Override public Double getDelaiMoyen() { return jours; }
        };
    }

    private static Utilisateur personne(Long id, String prenom, String nom, String profil) {
        return Utilisateur.builder().id(id).prenom(prenom).nom(nom)
                .profil(Profil.builder().code(profil).build()).build();
    }

    @Test
    @DisplayName("compte par personne ce que l'historique a enregistré")
    void compteParPersonne() {
        when(historiqueActionRepository.compterActivitesParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of(
                        activite(10L, TypeActionHistorique.DOSSIER_CREE, 14L),
                        activite(10L, TypeActionHistorique.AUDIENCE_PLANIFIEE, 9L),
                        activite(10L, TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE, 7L),
                        activite(10L, TypeActionHistorique.DELIBERE_ENREGISTRE, 3L),
                        activite(10L, TypeActionHistorique.LETTRE_CONSTITUTION_REDIGEE, 2L)));
        when(historiqueActionRepository.delaiMoyenDeSaisieParPersonne(any(), any()))
                .thenReturn(List.of(delai(10L, 2.3d)));
        when(utilisateurRepository.findAllById(any()))
                .thenReturn(List.of(personne(10L, "Jean", "Juriste", "JURISTE")));

        var rapport = service.produire(DEBUT, FIN);

        assertThat(rapport.lignes()).hasSize(1);
        var ligne = rapport.lignes().get(0);
        assertThat(ligne.nom()).isEqualTo("Jean Juriste");
        assertThat(ligne.profil()).isEqualTo("JURISTE");
        assertThat(ligne.dossiersOuverts()).isEqualTo(14L);
        assertThat(ligne.audiencesPlanifiees()).isEqualTo(9L);
        assertThat(ligne.comptesRendusSaisis()).isEqualTo(7L);
        assertThat(ligne.deliberesEnregistres()).isEqualTo(3L);
        assertThat(ligne.lettresRedigees()).isEqualTo(2L);
        assertThat(ligne.delaiMoyenSaisieCompteRendu()).isEqualTo(2.3d);
    }

    /**
     * Fees count **two** actions: compliance and opportunity are distinct reviews of the same request.
     * Keeping only one would hide half the work of someone doing both.
     */
    @Test
    @DisplayName("les deux contrôles de frais s'additionnent en un seul chiffre")
    void fraisAdditionnentLesDeuxControles() {
        when(historiqueActionRepository.compterActivitesParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of(
                        activite(10L, TypeActionHistorique.FRAIS_CONFORMITE_CONTROLEE, 5L),
                        activite(10L, TypeActionHistorique.FRAIS_OPPORTUNITE_CONTROLEE, 4L)));
        when(historiqueActionRepository.delaiMoyenDeSaisieParPersonne(any(), any())).thenReturn(List.of());
        when(utilisateurRepository.findAllById(any()))
                .thenReturn(List.of(personne(10L, "Jean", "Juriste", "JURISTE")));

        var rapport = service.produire(DEBUT, FIN);

        assertThat(rapport.lignes().get(0).fraisTraites()).isEqualTo(9L);
    }

    /**
     * Without an entered report, no delay: **null**, not zero. Zero would read as same-day entry, the
     * best possible performance.
     */
    @Test
    @DisplayName("le délai moyen est nul quand aucun compte rendu n'a été saisi")
    void delaiNulSansCompteRendu() {
        when(historiqueActionRepository.compterActivitesParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of(activite(10L, TypeActionHistorique.DOSSIER_CREE, 1L)));
        when(historiqueActionRepository.delaiMoyenDeSaisieParPersonne(any(), any())).thenReturn(List.of());
        when(utilisateurRepository.findAllById(any()))
                .thenReturn(List.of(personne(10L, "Jean", "Juriste", "JURISTE")));

        var rapport = service.produire(DEBUT, FIN);

        assertThat(rapport.lignes().get(0).delaiMoyenSaisieCompteRendu()).isNull();
    }

    /**
     * The report is sorted by **name**, never by volume. Ranking by number of actions would turn it into
     * a leaderboard, while thirty simple cases are not worth two difficult ones.
     */
    @Test
    @DisplayName("les lignes suivent l'ordre des noms, et non celui des volumes")
    void trieParNomEtNonParVolume() {
        when(historiqueActionRepository.compterActivitesParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of(
                        activite(10L, TypeActionHistorique.DOSSIER_CREE, 30L),
                        activite(20L, TypeActionHistorique.DOSSIER_CREE, 2L)));
        when(historiqueActionRepository.delaiMoyenDeSaisieParPersonne(any(), any())).thenReturn(List.of());
        when(utilisateurRepository.findAllById(any())).thenReturn(List.of(
                personne(10L, "Zoe", "Zamba", "JURISTE"),
                personne(20L, "Alice", "Abena", "JURISTE")));

        var rapport = service.produire(DEBUT, FIN);

        assertThat(rapport.lignes()).extracting(r -> r.nom())
                .containsExactly("Alice Abena", "Zoe Zamba");
    }

    /** Period without activity: nothing to report, the controller will return 204. */
    @Test
    @DisplayName("une période vide ne produit aucune ligne, et n'interroge pas les délais")
    void periodeVide() {
        when(historiqueActionRepository.compterActivitesParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of());

        var rapport = service.produire(DEBUT, FIN);

        assertThat(rapport.estVide()).isTrue();
        // No point computing delays for nobody.
        verify(historiqueActionRepository, never()).delaiMoyenDeSaisieParPersonne(any(), any());
        verify(utilisateurRepository, never()).findAllById(any());
    }

    /**
     * The end of the period is **inclusive**. Bounds are dates, actions are instants: stopping at
     * midnight on the 30th would silently lose the whole day.
     */
    @Test
    @DisplayName("la borne haute va jusqu'au lendemain de la date de fin")
    void laFinDePeriodeEstIncluse() {
        when(historiqueActionRepository.compterActivitesParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of());

        service.produire(DEBUT, FIN);

        ArgumentCaptor<LocalDateTime> basse = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> haute = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(historiqueActionRepository).compterActivitesParPersonne(anyCollection(), basse.capture(), haute.capture());
        assertThat(basse.getValue()).isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay());
        assertThat(haute.getValue()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay());
    }

    /**
     * The tracked natures are **these**, and no others. Adding one silently would show an indicator
     * nobody asked for, in a report that names people.
     */
    @Test
    @DisplayName("seules les sept natures arrêtées avec le métier sont comptées")
    void naturesSuiviesFigees() {
        when(historiqueActionRepository.compterActivitesParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of());

        service.produire(DEBUT, FIN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<TypeActionHistorique>> codes = ArgumentCaptor.forClass(Set.class);
        verify(historiqueActionRepository).compterActivitesParPersonne(codes.capture(), any(), any());
        assertThat(codes.getValue()).containsExactlyInAnyOrder(
                TypeActionHistorique.DOSSIER_CREE,
                TypeActionHistorique.AUDIENCE_PLANIFIEE,
                TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE,
                TypeActionHistorique.DELIBERE_ENREGISTRE,
                TypeActionHistorique.LETTRE_CONSTITUTION_REDIGEE,
                TypeActionHistorique.FRAIS_CONFORMITE_CONTROLEE,
                TypeActionHistorique.FRAIS_OPPORTUNITE_CONTROLEE);
    }

    /** An account removed from the reference data keeps its traces: they do not become anonymous. */
    @Test
    @DisplayName("une personne absente du référentiel garde sa ligne, sans nom inventé")
    void personneAbsenteDuReferentiel() {
        when(historiqueActionRepository.compterActivitesParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of(activite(99L, TypeActionHistorique.DOSSIER_CREE, 4L)));
        when(historiqueActionRepository.delaiMoyenDeSaisieParPersonne(any(), any())).thenReturn(List.of());
        when(utilisateurRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(utilisateurRepository.findById(any())).thenReturn(java.util.Optional.empty());

        var rapport = service.produire(DEBUT, FIN);

        assertThat(rapport.lignes()).hasSize(1);
        assertThat(rapport.lignes().get(0).utilisateurId()).isEqualTo(99L);
        assertThat(rapport.lignes().get(0).nom()).isNull();
        assertThat(rapport.lignes().get(0).dossiersOuverts()).isEqualTo(4L);
    }

    private static HistoriqueActionRepository.ActiviteDeposee depot(
            Long utilisateurId, String prenom, String nom, String identifiant,
            TypeActionHistorique code, long total) {
        return new HistoriqueActionRepository.ActiviteDeposee() {
            @Override public Long getUtilisateurId() { return utilisateurId; }
            @Override public String getNom() { return nom; }
            @Override public String getPrenom() { return prenom; }
            @Override public String getIdentifiant() { return identifiant; }
            @Override public String getKeycloakId() { return null; }
            @Override public TypeActionHistorique getCode() { return code; }
            @Override public long getTotal() { return total; }
        };
    }

    private static Avocat inscription(Long id, String nom, String compte) {
        Avocat avocat = new Avocat();
        avocat.setId(id);
        avocat.setNom(nom);
        avocat.setCompteKeycloak(compte);
        return avocat;
    }

    /**
     * Lawyer activity table. The internal table's six columns count internal production natures a
     * lawyer never performs (they **upload**; the legal officer records), hence a second table with its
     * own columns rather than a row of zeros.
     */
    @Test
    @DisplayName("compte les dépôts de chaque avocat, et le nomme depuis le répertoire")
    void compteLesDepotsDesAvocats() {
        when(historiqueActionRepository.compterDepotsParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of(
                        depot(21L, "Me", "Fotso", "me.fotso",
                                TypeActionHistorique.PUBLICATION_CR_DEPOSE, 6),
                        depot(21L, "Me", "Fotso", "me.fotso",
                                TypeActionHistorique.FRAIS_DEMANDE_DEPOSEE, 3),
                        depot(22L, "Me", "Nkolo", "me.nkolo",
                                TypeActionHistorique.EXPLICATION_REPONDUE, 1)));
        when(avocatRepository.findByCompteKeycloakIn(anyCollection()))
                .thenReturn(List.of(inscription(5L, "Cabinet Fotso & Associés", "me.fotso"),
                        inscription(7L, "Me Nkolo", "me.nkolo")));

        var rapport = service.produireAvocats(DEBUT, FIN);

        assertThat(rapport.lignes()).hasSize(2);
        // By name: "Cabinet..." before "Me Nkolo".
        var premier = rapport.lignes().get(0);
        assertThat(premier.nom()).isEqualTo("Cabinet Fotso & Associés");
        // The **directory** name, not the account's: it is the name management knows and the one on
        // retainer letters.
        assertThat(premier.avocatId()).isEqualTo(5L);
        assertThat(premier.utilisateurId()).isEqualTo(21L);
        assertThat(premier.comptesRendusDeposes()).isEqualTo(6);
        assertThat(premier.demandesFraisDeposees()).isEqualTo(3);
        assertThat(premier.autresPublicationsDeposees()).isZero();
        assertThat(rapport.lignes().get(1).explicationsFournies()).isEqualTo(1);
    }

    /**
     * The four counted natures are exclusive to lawyers (the three uploads and the explanation answer
     * are all `hasRole('AVOCAT')`). Grouping on them is enough, without a profile condition.
     */
    @Test
    @DisplayName("n'interroge que les quatre natures qu'un avocat accomplit")
    void seulesLesNaturesDAvocat() {
        when(historiqueActionRepository.compterDepotsParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of());

        service.produireAvocats(DEBUT, FIN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<TypeActionHistorique>> codes =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(historiqueActionRepository).compterDepotsParPersonne(codes.capture(), any(), any());
        assertThat(codes.getValue()).containsExactlyInAnyOrder(
                TypeActionHistorique.PUBLICATION_CR_DEPOSE,
                TypeActionHistorique.FRAIS_DEMANDE_DEPOSEE,
                TypeActionHistorique.PUBLICATION_AUTRE_DEPOSEE,
                TypeActionHistorique.EXPLICATION_REPONDUE);
        // None of the six natures of the internal table: a lawyer performs none.
        assertThat(codes.getValue()).doesNotContain(
                TypeActionHistorique.DOSSIER_CREE,
                TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE,
                TypeActionHistorique.FRAIS_CONFORMITE_CONTROLEE);
    }

    @Test
    @DisplayName("une période sans dépôt est vide, et n'interroge pas le répertoire")
    void periodeSansDepot() {
        when(historiqueActionRepository.compterDepotsParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.produireAvocats(DEBUT, FIN).estVide()).isTrue();
        verify(avocatRepository, never()).findByCompteKeycloakIn(anyCollection());
    }

    /**
     * A directory entry removed does not erase what the lawyer uploaded. The screen then opens it via the
     * account, hence a null `avocatId`, which is information rather than a gap.
     */
    @Test
    @DisplayName("un avocat retiré du répertoire garde ses dépôts, sous le nom de son compte")
    void avocatRetireDuRepertoire() {
        when(historiqueActionRepository.compterDepotsParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of(depot(30L, "Me", "Ancien", "me.ancien",
                        TypeActionHistorique.PUBLICATION_CR_DEPOSE, 2)));
        when(avocatRepository.findByCompteKeycloakIn(anyCollection())).thenReturn(List.of());

        var ligne = service.produireAvocats(DEBUT, FIN).lignes().get(0);

        assertThat(ligne.avocatId()).isNull();
        assertThat(ligne.nom()).isEqualTo("Me Ancien");
        assertThat(ligne.comptesRendusDeposes()).isEqualTo(2);
    }

    @Test
    @DisplayName("la borne haute couvre toute la journée de fin, comme le tableau interne")
    void borneHauteDesAvocats() {
        when(historiqueActionRepository.compterDepotsParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of());

        service.produireAvocats(DEBUT, FIN);

        ArgumentCaptor<LocalDateTime> haute = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(historiqueActionRepository)
                .compterDepotsParPersonne(anyCollection(), any(), haute.capture());
        assertThat(haute.getValue()).isEqualTo(FIN.plusDays(1).atStartOfDay());
    }
}
