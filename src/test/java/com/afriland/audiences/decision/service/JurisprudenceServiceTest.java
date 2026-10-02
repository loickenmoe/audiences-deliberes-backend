package com.afriland.audiences.decision.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.decision.domain.Jurisprudence;
import com.afriland.audiences.decision.domain.TypeArchiveJurisprudence;
import com.afriland.audiences.decision.repository.JurisprudenceRepository;
import com.afriland.audiences.decision.web.dto.JurisprudenceResponse;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JurisprudenceServiceTest {

    @Mock private JurisprudenceRepository jurisprudenceRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private EtapeProcedureRepository etapeProcedureRepository;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private MinioStorageService minioStorageService;

    private JurisprudenceService service;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        service = new JurisprudenceService(jurisprudenceRepository, dossierRepository,
                etapeProcedureRepository, historiqueActionService, minioStorageService);
        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
    }

    @Test
    void creer_rejectsEmptyFile() {
        MockMultipartFile vide = new MockMultipartFile("file", "", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.creer(null, TypeArchiveJurisprudence.GROSSE, List.of("saisie"),
                "Recouvrement", "TGI Douala", LocalDate.now(), vide))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void creer_rejectsNonPdf() {
        MockMultipartFile fichier = new MockMultipartFile("file", "decision.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.creer(null, TypeArchiveJurisprudence.GROSSE, List.of("saisie"),
                "Recouvrement", "TGI Douala", LocalDate.now(), fichier))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void creer_rejectsOversizedFile() {
        byte[] gros = new byte[11 * 1024 * 1024];
        MockMultipartFile fichier = new MockMultipartFile("file", "decision.pdf", "application/pdf", gros);

        assertThatThrownBy(() -> service.creer(null, TypeArchiveJurisprudence.GROSSE, List.of("saisie"),
                "Recouvrement", "TGI Douala", LocalDate.now(), fichier))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void creer_rejectsEmptyMotsCles() {
        MockMultipartFile fichier = new MockMultipartFile("file", "decision.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.creer(null, TypeArchiveJurisprudence.GROSSE, List.of(),
                "Recouvrement", "TGI Douala", LocalDate.now(), fichier))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void creer_withoutDossierUsesDeposerSansDossier() {
        MockMultipartFile fichier = new MockMultipartFile("file", "decision.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(minioStorageService.deposerSansDossier(fichier)).thenReturn("jurisprudence/decision.pdf");
        when(jurisprudenceRepository.save(any(Jurisprudence.class))).thenAnswer(inv -> {
            Jurisprudence j = inv.getArgument(0);
            j.setId(1L);
            return j;
        });
        when(minioStorageService.urlTelechargement("jurisprudence/decision.pdf")).thenReturn("https://minio/jurisprudence/decision.pdf");

        JurisprudenceResponse result = service.creer(null, TypeArchiveJurisprudence.GROSSE, List.of("saisie"),
                "Recouvrement", "TGI Douala", LocalDate.now(), fichier);

        assertThat(result.dossierId()).isNull();
        assertThat(result.urlTelechargement()).isEqualTo("https://minio/jurisprudence/decision.pdf");
        verify(minioStorageService, never()).deposer(any(Long.class), any(org.springframework.web.multipart.MultipartFile.class));
    }

    @Test
    void creer_withDossierUsesDeposer() {
        MockMultipartFile fichier = new MockMultipartFile("file", "decision.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        // Le dossier est terminé : c'est la condition du versement (2026-09-29), dite ici plutôt
        // que laissée au défaut du mock — sans quoi ce test ne prouverait pas qu'elle est remplie.
        when(etapeProcedureRepository.existsByDossierIdAndStatutCycleVieNotIn(1L, StatutCycleVie.FERMES))
                .thenReturn(false);
        when(minioStorageService.deposer(1L, fichier)).thenReturn("dossiers/1/decision.pdf");
        when(jurisprudenceRepository.save(any(Jurisprudence.class))).thenAnswer(inv -> {
            Jurisprudence j = inv.getArgument(0);
            j.setId(2L);
            return j;
        });
        when(minioStorageService.urlTelechargement("dossiers/1/decision.pdf")).thenReturn("https://minio/dossiers/1/decision.pdf");

        JurisprudenceResponse result = service.creer(1L, TypeArchiveJurisprudence.EXPEDITION, List.of("hypotheque"),
                "Recouvrement", "TGI Douala", LocalDate.now(), fichier);

        assertThat(result.dossierId()).isEqualTo(1L);
        verify(minioStorageService, never()).deposerSansDossier(any());
    }

    /**
     * La garde du 2026-09-29 : une décision ne rejoint la base jurisprudentielle qu'une fois
     * définitive. {@code Jurisprudence} n'enregistrant rien qui dise l'état de la décision, une
     * décision frappée d'appel y entrerait au même titre qu'un arrêt définitif — et le juriste qui
     * la trouve comme précédent ne pourrait pas savoir qu'elle a peut-être été infirmée.
     */
    @Test
    void creer_refuseTantQuUneEtapeDuDossierEstEnCours() {
        MockMultipartFile fichier = new MockMultipartFile("file", "decision.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.existsByDossierIdAndStatutCycleVieNotIn(1L, StatutCycleVie.FERMES))
                .thenReturn(true);

        assertThatThrownBy(() -> service.creer(1L, TypeArchiveJurisprudence.EXPEDITION, List.of("hypotheque"),
                "Recouvrement", "TGI Douala", LocalDate.now(), fichier))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("encore une étape en cours")
                .hasMessageContaining("définitive");

        // Rien n'a été déposé ni enregistré : le refus tombe avant tout effet.
        verify(minioStorageService, never()).deposer(any(Long.class), any(org.springframework.web.multipart.MultipartFile.class));
        verify(jurisprudenceRepository, never()).save(any(Jurisprudence.class));
    }

    /**
     * Une jurisprudence <b>sans dossier</b> reste libre : elle sert à verser un arrêt trouvé
     * ailleurs, dont rien ne permet — ni n'a à permettre — de juger l'état.
     */
    @Test
    void creer_sansDossierNInterrogeAucuneEtape() {
        MockMultipartFile fichier = new MockMultipartFile("file", "decision.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(minioStorageService.deposerSansDossier(fichier)).thenReturn("jurisprudence/decision.pdf");
        when(jurisprudenceRepository.save(any(Jurisprudence.class))).thenAnswer(inv -> {
            Jurisprudence j = inv.getArgument(0);
            j.setId(3L);
            return j;
        });

        service.creer(null, TypeArchiveJurisprudence.EXPEDITION, List.of("hypotheque"),
                "Recouvrement", "TGI Douala", LocalDate.now(), fichier);

        verify(etapeProcedureRepository, never())
                .existsByDossierIdAndStatutCycleVieNotIn(any(), any());
    }

    @Test
    void rechercher_delegatesToRepository() {
        Jurisprudence jurisprudence = Jurisprudence.builder().id(1L).typeArchive(TypeArchiveJurisprudence.GROSSE)
                .motsCles(List.of("saisie")).natureDecision("Recouvrement").juridiction("TGI Douala").dateDecision(LocalDate.now()).build();
        Page<Jurisprudence> page = new PageImpl<>(List.of(jurisprudence));
        // Q-89 : le terme devient un motif « contient », en minuscules.
        when(jurisprudenceRepository.rechercher(eq("%saisie%"), eq(null), eq(null), eq(null), any())).thenReturn(page);

        var result = service.rechercher("Saisie", null, null, null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).natureDecision()).isEqualTo("Recouvrement");
    }

    @Test
    void rechercher_passesTheDossierAndIgnoresBlankFilters() {
        when(jurisprudenceRepository.rechercher(eq(null), eq(null), eq("%tgi douala%"), eq(3L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.rechercher("  ", "", " TGI Douala ", 3L, PageRequest.of(0, 20));

        assertThat(result.content()).isEmpty();
    }

    @Test
    void motif_escapesTheLikeWildcardsTypedByTheUser() {
        assertThat(JurisprudenceService.motif("50%_a\\b")).isEqualTo("%50\\%\\_a\\\\b%");
        assertThat(JurisprudenceService.motif(null)).isNull();
        assertThat(JurisprudenceService.motif("   ")).isNull();
    }
}
