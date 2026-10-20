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
 * Audit extraction.
 *
 * <p>These tests do not check formatting but the chain that makes the archive enforceable: the
 * fingerprint covers the stored bytes, the period is mandatory, and an oversized extraction is
 * refused rather than truncated.
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
        when(exportAuditRepository.prochainNumeroReference()).thenReturn(42L);
        when(renderer.pdf(any(), any(), any(), anyString(), anyString())).thenReturn(contenu);
    }

    /**
     * The fingerprint covers the <b>stored bytes</b>, never the query that produced them: the file is
     * what will be verified years later.
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

        // The stored bytes are these, not a second generation that could differ.
        ArgumentCaptor<byte[]> deposes = ArgumentCaptor.forClass(byte[].class);
        verify(minioStorageService).deposerArchiveAudit(anyString(), anyString(), deposes.capture());
        assertThat(deposes.getValue()).isEqualTo(contenu);
    }

    /** The extraction is logged with its fingerprint, so the log entry and the file can be matched. */
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
        // The reference is in the log entry: it links the action to the file.
        assertThat(captor.getValue().get("reference")).asString().startsWith("AUD-");
    }

    /**
     * Above the cap, the extraction is refused rather than truncated: a truncated archive would
     * misstate its coverage, and its fingerprint would seal that error.
     */
    @Test
    void exporter_refuseUneExtractionTropVasteAuLieuDeLaTronquer() {
        when(historiqueActionRepository.count(specification()))
                .thenReturn((long) AuditExportService.PLAFOND_LIGNES + 1);

        var date = LocalDate.of(2020, 1, 1);
        var date2 = LocalDate.of(2030, 1, 1);
        assertThatThrownBy(() -> auditExportService.exporter(Perimetre.TOUT, null, null, null, date, date2, FormatExportAudit.EXCEL))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Restreignez la période");

        verify(minioStorageService, never()).deposerArchiveAudit(anyString(), anyString(), any());
        verify(exportAuditRepository, never()).save(any());
    }

    /**
     * The period is mandatory (unlike on screen): an archive without a period does not say what it
     * contains, and its fingerprint would only prove its integrity.
     */
    @Test
    void exporter_exigeUnePeriode() {
        var date = LocalDate.of(2026, 9, 30);
        assertThatThrownBy(() -> auditExportService.exporter(Perimetre.TOUT, null, null, null, null, date, FormatExportAudit.PDF))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exige une période");
    }

    @Test
    void exporter_refuseUneFenetreInverseeEtUnDossierManquant() {
        var date4 = LocalDate.of(2026, 9, 30);
        var date5 = LocalDate.of(2026, 9, 1);
        assertThatThrownBy(() -> auditExportService.exporter(Perimetre.TOUT, null, null, null, date4, date5, FormatExportAudit.PDF))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("date de début");

        var date2 = LocalDate.of(2026, 9, 1);
        var date3 = LocalDate.of(2026, 9, 30);
        assertThatThrownBy(() -> auditExportService.exporter(Perimetre.UN_DOSSIER, null, null, null, date2, date3, FormatExportAudit.PDF))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("identifiant du dossier");
    }

    /** Downloading an archive leaves a trace; otherwise it could be accessed unnoticed. */
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
     * A known fingerprint, so that the computation itself cannot drift silently: the SHA-256 of
     * "abc", verifiable outside this project.
     */
    @Test
    void empreinte_estUnSha256Hexadecimal() {
        assertThat(AuditExportService.empreinte("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    /**
     * The reference is read <b>before</b> the file is produced and passed in the title to the
     * renderer: it is the only archive identifier the file can contain.
     */
    @Test
    void exporter_poseLaReferenceDansLeFichierEtDansLArchive() {
        byte[] contenu = {9};
        extractionVide(contenu);

        var extraction = auditExportService.exporter(Perimetre.TOUT, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), FormatExportAudit.PDF);

        assertThat(extraction.archive().getReference()).matches("AUD-\\d{4}-0042");
        assertThat(extraction.nomFichier()).startsWith("aud-");

        ArgumentCaptor<String> titre = ArgumentCaptor.forClass(String.class);
        verify(renderer).pdf(any(), any(), any(), titre.capture(), anyString());
        assertThat(titre.getValue()).contains(extraction.archive().getReference());
    }

    /**
     * Verification is what makes the fingerprint useful: otherwise one would have to compare
     * 64 characters by eye in a terminal.
     */
    @Test
    void verifier_reconnaitUnFichierQuiCorrespond() {
        String empreinte = AuditExportService.empreinte("abc".getBytes(StandardCharsets.UTF_8));
        ExportAudit archive = ExportAudit.builder().id(7L).reference("AUD-2026-0007")
                .empreinte(empreinte).perimetre(Perimetre.TOUT).format(FormatExportAudit.PDF).build();
        when(exportAuditRepository.findByEmpreinte(empreinte)).thenReturn(Optional.of(archive));

        var resultat = auditExportService.verifier(empreinte.toUpperCase());

        // Case-insensitive: an auditor copies a fingerprint as they see it.
        assertThat(resultat.reconnue()).isTrue();
        assertThat(resultat.archive().reference()).isEqualTo("AUD-2026-0007");
    }

    /**
     * An unknown file is not an error but an answer; a 404 would make it look like an application
     * failure.
     */
    @Test
    void verifier_repondSansErreurQuandRienNeCorrespond() {
        String empreinte = "a".repeat(64);
        when(exportAuditRepository.findByEmpreinte(empreinte)).thenReturn(Optional.empty());

        var resultat = auditExportService.verifier(empreinte);

        assertThat(resultat.reconnue()).isFalse();
        assertThat(resultat.archive()).isNull();
    }

    /** Anything that is not a fingerprint is refused: searching the database for arbitrary text makes no sense. */
    @Test
    void verifier_refuseCeQuiNEstPasUneEmpreinte() {
        for (String invalide : List.of("", "bonjour", "abc", "z".repeat(64), "a".repeat(63))) {
            assertThatThrownBy(() -> auditExportService.verifier(invalide))
                    .as("empreinte invalide : %s", invalide)
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("64 caractères");
        }
    }
}
