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
 * L'activité de chacun sur une période (observation 14, N6).
 *
 * <p>Ce qui est éprouvé : que le rapport **compte ce qui a été fait**, qu'il ne fabrique rien là où
 * l'historique est muet, et qu'il refuse les deux tentations d'un rapport nominatif - classer, et
 * additionner des gestes qui ne s'additionnent pas.
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
     * Les frais comptent **deux** gestes.
     *
     * <p>Conformité et opportunité sont deux contrôles distincts sur la même demande : n'en retenir
     * qu'un rendrait invisible la moitié du travail de celui qui fait les deux.
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
     * Sans compte rendu saisi, pas de délai : **nul**, et non zéro.
     *
     * <p>Zéro se lirait comme une saisie le jour même de l'audience, c'est-à-dire comme la
     * meilleure performance possible. L'absence de mesure n'est pas une mesure excellente.
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
     * Le rapport est trié par **nom**, jamais par volume.
     *
     * <p>Classer par nombre d'actions ferait du rapport un palmarès, et le premier de la liste
     * passerait pour le meilleur - alors que trente dossiers simples ne valent pas deux affaires
     * difficiles. L'application compte ; le responsable apprécie.
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

    /** Période sans activité : rien à dire, et le contrôleur rendra 204. */
    @Test
    @DisplayName("une période vide ne produit aucune ligne, et n'interroge pas les délais")
    void periodeVide() {
        when(historiqueActionRepository.compterActivitesParPersonne(anyCollection(), any(), any()))
                .thenReturn(List.of());

        var rapport = service.produire(DEBUT, FIN);

        assertThat(rapport.estVide()).isTrue();
        // Inutile de calculer des délais pour personne.
        verify(historiqueActionRepository, never()).delaiMoyenDeSaisieParPersonne(any(), any());
        verify(utilisateurRepository, never()).findAllById(any());
    }

    /**
     * La fin de période est **incluse**.
     *
     * <p>Un rapport « du 1er au 30 » doit compter le 30. Les bornes sont des dates, les actions des
     * instants : s'arrêter au 30 à minuit perdrait la journée entière sans que personne le voie.
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
     * Les natures suivies sont **celles-là**, et pas d'autres.
     *
     * <p>Ce test fige la liste arrêtée avec le métier : l'y ajouter une nature sans le dire ferait
     * apparaître un indicateur que personne n'a demandé, dans un rapport qui nomme des personnes.
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

    /** Un compte retiré du référentiel laisse ses traces : elles ne deviennent pas anonymes. */
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
     * **Le tableau que le porteur du projet a reclame le 2026-10-13.** « Activite par personne » ne
     * montrait que les profils internes, et c'etait exact : ses six colonnes comptent des natures de
     * production interne qu'un avocat n'accomplit jamais. Il **depose** ; c'est le juriste qui
     * enregistre. Un second tableau, avec ses propres colonnes, plutot qu'une ligne de zeros.
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
        // Par nom : « Cabinet… » avant « Me Nkolo ».
        var premier = rapport.lignes().get(0);
        assertThat(premier.nom()).isEqualTo("Cabinet Fotso & Associés");
        // Le nom du **répertoire**, pas celui du compte : c'est sous celui-là que la direction le
        // connaît, et c'est lui qui figure sur les lettres de constitution.
        assertThat(premier.avocatId()).isEqualTo(5L);
        assertThat(premier.utilisateurId()).isEqualTo(21L);
        assertThat(premier.comptesRendusDeposes()).isEqualTo(6);
        assertThat(premier.demandesFraisDeposees()).isEqualTo(3);
        assertThat(premier.autresPublicationsDeposees()).isZero();
        assertThat(rapport.lignes().get(1).explicationsFournies()).isEqualTo(1);
    }

    /**
     * Les quatre natures comptees sont exclusives a l'avocat - les trois depots et la reponse a une
     * demande d'explication sont tous `hasRole('AVOCAT')`. Grouper sur elles suffit donc, sans
     * condition sur le profil : une condition de moins a garder d'accord avec les habilitations.
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
        // Aucune des six natures du tableau interne : un avocat n'en accomplit aucune.
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
     * Une inscription retiree du repertoire ne fait pas disparaitre ce que l'avocat a depose. L'ecran
     * l'ouvre alors par son compte, faute d'inscription a designer - d'ou un `avocatId` nul, qui est
     * une information et non un trou.
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
