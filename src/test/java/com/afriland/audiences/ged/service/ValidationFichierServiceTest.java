package com.afriland.audiences.ged.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.ged.domain.FormatDocument;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Format and size validation, tested on its own.
 *
 * <p>This service decides what the application accepts: it is shared by the GED, fee request
 * supporting documents and hearing report attachments. Testing it through {@code GedService} only
 * covered the first.
 */
@ExtendWith(MockitoExtension.class)
class ValidationFichierServiceTest {

    private static final String TOUS_LES_FORMATS = "PDF,PNG,JPG,TXT,XLSX,DOCX,DOC";

    @Mock
    private ConfigurationService configurationService;

    @InjectMocks
    private ValidationFichierService service;

    @BeforeEach
    void configurationParDefaut() {
        // `lenient`: cases failing on the extension never reach the configuration.
        lenient().when(configurationService.getOrThrow("FORMATS_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur(TOUS_LES_FORMATS).build());
        lenient().when(configurationService.getOrThrow("TAILLE_MAX_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("10485760").build());
    }

    private static MockMultipartFile fichier(String nom) {
        return new MockMultipartFile("file", nom, "application/octet-stream", new byte[]{1, 2, 3});
    }

    @ParameterizedTest
    @CsvSource({
            "conclusions.docx, DOCX",
            "assignation.doc, DOC",
            "decision.pdf, PDF",
            "scan.png, PNG",
            "note.txt, TXT",
            "etat.xlsx, XLSX"
    })
    void valider_reconnaitLesFormatsAdmis(String nom, FormatDocument attendu) {
        assertThat(service.valider(fichier(nom))).isEqualTo(attendu);
    }

    /** The extension is case-insensitive: Word often writes ".DOCX". */
    @ParameterizedTest
    @CsvSource({"CONCLUSIONS.DOCX, DOCX", "Assignation.Doc, DOC"})
    void valider_ignoreLaCasseDeLExtension(String nom, FormatDocument attendu) {
        assertThat(service.valider(fichier(nom))).isEqualTo(attendu);
    }

    /**
     * The format comes from the extension, not the declared MIME type. A Word document sent as
     * {@code application/octet-stream} is therefore accepted, by design.
     */
    @Test
    void valider_sAppuieSurLExtensionEtNonSurLeTypeMime() {
        MockMultipartFile trompeur =
                new MockMultipartFile("file", "note.docx", "text/plain", new byte[]{1});
        assertThat(service.valider(trompeur)).isEqualTo(FormatDocument.DOCX);
    }

    @Test
    void valider_refuseUnFormatInconnu() {
        var fichier = fichier("texte.odt");
        assertThatThrownBy(() -> service.valider(fichier))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("non conforme");
    }

    @Test
    void valider_refuseUnFichierSansExtension() {
        var fichier = fichier("conclusions");
        assertThatThrownBy(() -> service.valider(fichier))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("extension");
    }

    /** The DJ can remove Word from the list: the refusal then says so instead of reporting an unknown format. */
    @Test
    void valider_refuseUnFormatDesactiveParLaConfiguration() {
        when(configurationService.getOrThrow("FORMATS_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("PDF,PNG,JPG").build());

        var fichier = fichier("conclusions.docx");
        assertThatThrownBy(() -> service.valider(fichier))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("désactivé");
    }

    @Test
    void valider_refuseUnFichierTropLourd() {
        when(configurationService.getOrThrow("TAILLE_MAX_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("2").build());

        var fichier = fichier("conclusions.docx");
        assertThatThrownBy(() -> service.valider(fichier))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("supérieur au maximum");
    }
}
