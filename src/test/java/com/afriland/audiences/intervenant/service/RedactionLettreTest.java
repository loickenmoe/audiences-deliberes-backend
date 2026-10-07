package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.LettreConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.mapper.DemandeConstitutionMapper;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.intervenant.repository.LettreConstitutionRepository;
import com.afriland.audiences.intervenant.web.dto.RedigerLettreRequest;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import com.afriland.audiences.referentiel.service.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La rédaction de la lettre, et les deux règles qui la bornent (Q-115 B2).
 *
 * <p><b>Le gel.</b> Une lettre signée est partie chez un huissier et figure dans la GED. La retoucher
 * en silence ferait mentir la pièce : une correction passe par une nouvelle demande, donc une
 * nouvelle lettre et un nouveau numéro, et la première reste consultable. C'est le raisonnement déjà
 * retenu pour les archives d'audit (Q-113).
 *
 * <p><b>L'unicité du numéro.</b> Le compteur est modifiable — la direction juridique l'a demandé —
 * mais deux lettres ne peuvent pas porter le même numéro : dans une série de courrier officiel, un
 * numéro servi deux fois ne désigne plus rien.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedactionLettreTest {

    @Mock private DemandeConstitutionRepository demandeConstitutionRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private IntervenantRepository intervenantRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private AlerteService alerteService;
    @Mock private LettreConstitutionRepository lettreConstitutionRepository;
    @Mock private MinioStorageService minioStorageService;
    @Mock private DocumentRepository documentRepository;
    @Mock private DemandeConstitutionMapper mapper;
    @Mock private com.afriland.audiences.referentiel.repository.AvocatRepository avocatRepository;

    private ConstitutionService service;
    private DemandeConstitution demande;

    @Mock
    private NotificationPrestataireService notificationPrestataireService;

    /** Celui qui a sollicité la constitution : la lettre est la sienne (Q-126). */
    private Utilisateur auteur;

    @BeforeEach
    void setUp() {
        service = new ConstitutionService(demandeConstitutionRepository, dossierRepository, intervenantRepository,
                utilisateurRepository, currentUserService, historiqueActionService, alerteService,
                new LettreConstitutionService(),
                // Aucune signature déposée dans ce parcours : la lettre part avec le nom seul, comme
                // avant N8. C'est le cas de tous ceux qui n'ont rien déposé.
                new SignatureService(utilisateurRepository, currentUserService),
                lettreConstitutionRepository, minioStorageService,
                documentRepository, mapper,
                new CompteAvocatService(avocatRepository, utilisateurRepository), notificationPrestataireService);

        /*
         * La demande porte son demandeur, comme en base : la colonne est `nullable = false`, et une
         * demande sans demandeur n'existe pas. Depuis Q-126 elle lui appartient, et c'est lui qui
         * écrit la lettre - le décor doit donc le nommer.
         */
        auteur = Utilisateur.builder().id(10L).nom("Juriste").prenom("Jean").build();
        demande = DemandeConstitution.builder()
                .id(100L)
                .dossier(Dossier.builder().id(1L).reference("DOS-2026-001").build())
                .prestataire(Avocat.builder().id(5L).nom("Me Fotso").build())
                .demandeur(auteur)
                .statut(StatutConstitution.EN_ATTENTE)
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(auteur);
        when(lettreConstitutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private LettreConstitution brouillon() {
        return new LettreConstitutionService().brouillonPour(demande, null);
    }

    private static RedigerLettreRequest requete(Integer numero) {
        return new RedigerLettreRequest(numero, "First Bank/DAJR", "Douala", "Me Kamga",
                "Huissier de Justice", "Douala", "DOSSIER DOS-2026-001", "Saisie-attribution",
                "Nous vous constituons.", "Le Département du Recouvrement");
    }

    @Test
    @DisplayName("le juriste enregistre sa rédaction, et l'action est historisée")
    void redactionEnregistreeEtHistorisee() {
        when(lettreConstitutionRepository.findByDemandeId(100L)).thenReturn(Optional.of(brouillon()));

        var reponse = service.redigerLettre(100L, requete(null));

        assertThat(reponse.objet()).isEqualTo("Saisie-attribution");
        assertThat(reponse.destinataireNom()).isEqualTo("Me Kamga");
        assertThat(reponse.modifiable()).isTrue();
        // La lettre engage la banque : savoir qui l'a rédigée fait partie de la pièce.
        verify(historiqueActionService).enregistrerPour(any(), eq(1L),
                eq(TypeActionHistorique.LETTRE_CONSTITUTION_REDIGEE), any());
    }

    /**
     * Un autre juriste ne récrit pas la lettre de celui-ci (métier, 2026-10-07).
     *
     * <p>C'était possible jusqu'au 2026-10-07 : le droit tenait au rôle, et {@code ROLE_JURISTE}
     * étant composite (Q-81), tout juriste, le DJ et la DJA passaient. Une lettre engage la banque
     * sous le nom de celui qui l'a écrite - nul ne doit pouvoir écrire sous le nom d'un autre.
     */
    @Test
    @DisplayName("un autre juriste ne récrit pas la lettre d'un collègue")
    void unAutreJuristeEstRefuse() {
        when(lettreConstitutionRepository.findByDemandeId(100L)).thenReturn(Optional.of(brouillon()));
        when(currentUserService.getCurrentUser())
                .thenReturn(Utilisateur.builder().id(77L).nom("Autre").prenom("Juriste")
                        .profil(Profil.builder().code("JURISTE").build()).build());

        assertThatThrownBy(() -> service.redigerLettre(100L, requete(null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("appartient au demandeur");
        // Le refus est total : rien n'est enregistré, rien n'est historisé.
        verify(lettreConstitutionRepository, never()).save(any());
        verify(historiqueActionService, never()).enregistrerPour(any(), any(), any(), any());
    }

    /**
     * Le supérieur hiérarchique, lui, passe (Q-124).
     *
     * <p>Il corrige au lieu de renvoyer la lettre : c'est la contrepartie de la plume retirée aux
     * autres. Le droit se lit sur son <b>profil</b>, non sur un rôle - un rôle composite rouvrirait
     * la porte au DJ et à la DJA.
     */
    @Test
    @DisplayName("le supérieur hiérarchique corrige la lettre d'un autre")
    void leShPasse() {
        when(lettreConstitutionRepository.findByDemandeId(100L)).thenReturn(Optional.of(brouillon()));
        when(currentUserService.getCurrentUser())
                .thenReturn(Utilisateur.builder().id(30L).nom("Sup").prenom("Hierarchique")
                        .profil(Profil.builder().code("SH").build()).build());

        var reponse = service.redigerLettre(100L, requete(null));

        assertThat(reponse.objet()).isEqualTo("Saisie-attribution");
        verify(lettreConstitutionRepository).save(any());
    }

    @Test
    @DisplayName("une lettre déjà signée ne se réécrit pas")
    void lettreGeleeRefusee() {
        LettreConstitution gelee = brouillon();
        gelee.setNumero(33);
        gelee.setDateGeneration(LocalDateTime.now());
        when(lettreConstitutionRepository.findByDemandeId(100L)).thenReturn(Optional.of(gelee));

        assertThatThrownBy(() -> service.redigerLettre(100L, requete(34)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ne peut plus être modifiée");
        // Et rien n'est enregistré : le refus doit être total, pas partiel.
        verify(lettreConstitutionRepository, never()).save(any());
        verify(historiqueActionService, never()).enregistrerPour(any(), any(), any(), any());
    }

    @Test
    @DisplayName("un numéro déjà employé est refusé")
    void numeroDejaEmployeRefuse() {
        when(lettreConstitutionRepository.findByDemandeId(100L)).thenReturn(Optional.of(brouillon()));
        when(lettreConstitutionRepository.existsByNumero(33)).thenReturn(true);

        assertThatThrownBy(() -> service.redigerLettre(100L, requete(33)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà porté");
    }

    /**
     * Le cas courant : le rédacteur n'impose rien. Le numéro reste vide et sera tiré de la séquence
     * à la signature — ce qui évite d'en brûler un sur une demande sans suite.
     */
    @Test
    @DisplayName("sans numéro imposé, le brouillon reste sans numéro")
    void sansNumeroImposeLeBrouillonResteSansNumero() {
        when(lettreConstitutionRepository.findByDemandeId(100L)).thenReturn(Optional.of(brouillon()));

        var reponse = service.redigerLettre(100L, requete(null));

        assertThat(reponse.numero()).isNull();
        verify(lettreConstitutionRepository, never()).prochainNumero();
    }

    @Test
    @DisplayName("relire sa lettre ne la modifie pas")
    void lectureNeModifiePas() {
        when(lettreConstitutionRepository.findByDemandeId(100L)).thenReturn(Optional.of(brouillon()));

        var reponse = service.lettre(100L);

        assertThat(reponse.demandeId()).isEqualTo(100L);
        assertThat(reponse.objet()).isEqualTo(LettreConstitutionService.OBJET_PAR_DEFAUT);
        verify(lettreConstitutionRepository, never()).save(any());
    }

    @Test
    @DisplayName("une demande sans lettre est signalée, pas silencieuse")
    void demandeSansLettre() {
        when(lettreConstitutionRepository.findByDemandeId(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lettre(404L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucune lettre");
    }
}
