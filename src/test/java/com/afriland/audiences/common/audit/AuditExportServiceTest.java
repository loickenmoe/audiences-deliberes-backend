package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extraction de l'audit (Q-113).
 *
 * <p>Ce que ces tests protègent n'est pas la mise en forme — un test ne sait pas si un PDF est
 * lisible — mais la chaîne qui rend l'archive opposable : l'empreinte porte sur les octets déposés,
 * la période est obligatoire, et une extraction trop vaste est refusée plutôt que tronquée.
 */
@ExtendWith(MockitoExtension.class)
class AuditExportServiceTest {

    @Mock private HistoriqueActionRepository historiqueActionRepository;
    @Mock private ExportAuditRepository exportAuditRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private AuditExportRenderer renderer;
    @Mock private MinioStorageService minioStorageService;
    @Mock private CurrentUserService currentUserService;

    @InjectMocks private AuditExportService auditExportService;

    private static Specification<HistoriqueAction> specification() {
        return ArgumentMatchers.any();
    }

    private void extractionVide(byte[] contenu) {
        when(historiqueActionRepository.count(specification())).thenReturn(0L);
        when(historiqueActionRepository.findAll(specification(), any(Sort.class))).thenReturn(List.of());
        when(utilisateurRepository.findAll()).thenReturn(List.of());
        when(currentUserService.getCurrentUser()).thenReturn(Utilisateur.builder().id(3L).build());
        when(minioStorageService.deposerArchiveAudit(anyString(), anyString(), any())).thenReturn("audit/x.pdf");
        when(exportAuditRepository.save(any(ExportAudit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(renderer.pdf(any(), any(), any(), anyString())).thenReturn(contenu);
    }

    /**
     * L'empreinte porte sur les <b>octets déposés</b>, jamais sur la requête qui les a produits :
     * c'est le fichier qu'on pourra vérifier des années plus tard.
     */
    @Test
    void exporter_scelleLesOctetsReellementDeposes() {
        byte[] contenu = "contenu-de-test".getBytes(StandardCharsets.UTF_8);
        extractionVide(contenu);

        var extraction = auditExportService.exporter(Perimetre.TOUT, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), FormatExportAudit.PDF);

        assertThat(extraction.contenu()).isEqualTo(contenu);
        assertThat(extraction.archive().getEmpreinte()).isEqualTo(AuditExportService.empreinte(contenu));
        assertThat(extraction.archive().getEmpreinte()).hasSize(64);
        assertThat(extraction.archive().getTailleOctets()).isEqualTo(contenu.length);

        // Les octets déposés sont ceux-là, et non une seconde génération qui pourrait différer.
        ArgumentCaptor<byte[]> deposes = ArgumentCaptor.forClass(byte[].class);
        verify(minioStorageService).deposerArchiveAudit(anyString(), anyString(), deposes.capture());
        assertThat(deposes.getValue()).isEqualTo(contenu);
    }

    /** L'extraction est historisée avec son empreinte : la trace et la pièce se recoupent. */
    @Test
    void exporter_historiseLExtractionAvecSonEmpreinte() {
        byte[] contenu = {1, 2, 3};
        extractionVide(contenu);

        auditExportService.exporter(Perimetre.TOUT, null, 42L, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), FormatExportAudit.PDF);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(historiqueActionService).enregistrer(eq(null), eq(TypeActionHistorique.AUDIT_EXPORTE), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("empreinte", AuditExportService.empreinte(contenu))
                .containsEntry("format", "PDF")
                .containsEntry("auteurRecherche", 42L)
                .containsEntry("lignes", 0);
    }

    /**
     * Au-delà du plafond, on refuse au lieu de tronquer. Une archive tronquée mentirait sur ce
     * qu'elle couvre, et son empreinte scellerait ce mensonge.
     */
    @Test
    void exporter_refuseUneExtractionTropVasteAuLieuDeLaTronquer() {
        when(historiqueActionRepository.count(specification()))
                .thenReturn((long) AuditExportService.PLAFOND_LIGNES + 1);

        assertThatThrownBy(() -> auditExportService.exporter(Perimetre.TOUT, null, null, null,
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), FormatExportAudit.EXCEL))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Restreignez la période");

        verify(minioStorageService, never()).deposerArchiveAudit(anyString(), anyString(), any());
        verify(exportAuditRepository, never()).save(any());
    }

    /**
     * La période est obligatoire, alors qu'elle est facultative à l'écran : une archive sans période
     * ne dit pas ce qu'elle contient, et son empreinte ne prouverait que son intégrité.
     */
    @Test
    void exporter_exigeUnePeriode() {
        assertThatThrownBy(() -> auditExportService.exporter(Perimetre.TOUT, null, null, null,
                null, LocalDate.of(2026, 9, 30), FormatExportAudit.PDF))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exige une période");
    }

    @Test
    void exporter_refuseUneFenetreInverseeEtUnDossierManquant() {
        assertThatThrownBy(() -> auditExportService.exporter(Perimetre.TOUT, null, null, null,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), FormatExportAudit.PDF))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("date de début");

        assertThatThrownBy(() -> auditExportService.exporter(Perimetre.UN_DOSSIER, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), FormatExportAudit.PDF))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("identifiant du dossier");
    }

    /** Récupérer une archive laisse une trace : sinon on y puiserait sans que rien ne le dise. */
    @Test
    void telecharger_traceLaRecuperation() {
        ExportAudit archive = ExportAudit.builder().id(7L).empreinte("abc").cleStockage("audit/x.pdf").build();
        when(exportAuditRepository.findById(7L)).thenReturn(Optional.of(archive));
        when(minioStorageService.urlTelechargement("audit/x.pdf")).thenReturn("https://minio/x");

        String url = auditExportService.telecharger(7L);

        assertThat(url).isEqualTo("https://minio/x");
        verify(historiqueActionService).enregistrer(eq(null), eq(TypeActionHistorique.ARCHIVE_TELECHARGEE),
                eq(Map.of("exportId", 7L, "empreinte", "abc")));
    }

    @Test
    void telecharger_refuseUneArchiveInconnue() {
        when(exportAuditRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditExportService.telecharger(99L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("introuvable");

        verify(historiqueActionService, never()).enregistrer(any(), any(), any());
    }

    /**
     * Une empreinte connue, pour que le calcul lui-même ne dérive pas silencieusement : c'est le
     * SHA-256 de « abc », vérifiable hors de ce projet.
     */
    @Test
    void empreinte_estUnSha256Hexadecimal() {
        assertThat(AuditExportService.empreinte("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
