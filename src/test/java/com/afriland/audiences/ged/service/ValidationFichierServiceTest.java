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
 * Contrôle de format et de poids, pour lui-même.
 *
 * <p>Ce service décide ce que l'application accepte de recevoir : il est partagé par la GED, les
 * pièces justificatives d'une demande de frais (Q-97) et la pièce d'un compte rendu (Q-98). Le
 * tester à travers {@code GedService} ne couvrait que la première de ces trois portes.
 *
 * <p>Écrit à l'ajout des formats Word (2026-10-02).
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
        // `lenient` : les cas qui échouent sur l'extension n'atteignent jamais la configuration.
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

    /** L'extension est lue sans égard à la casse : Word écrit volontiers « .DOCX ». */
    @ParameterizedTest
    @CsvSource({"CONCLUSIONS.DOCX, DOCX", "Assignation.Doc, DOC"})
    void valider_ignoreLaCasseDeLExtension(String nom, FormatDocument attendu) {
        assertThat(service.valider(fichier(nom))).isEqualTo(attendu);
    }

    /**
     * Le format vient de l'extension, pas du type MIME déclaré (Q-46). Un document Word envoyé
     * sous un type MIME générique est donc accepté — c'est la règle, et la connaître évite de
     * croire à un défaut le jour où un navigateur déclare {@code application/octet-stream}.
     */
    @Test
    void valider_sAppuieSurLExtensionEtNonSurLeTypeMime() {
        MockMultipartFile trompeur =
                new MockMultipartFile("file", "note.docx", "text/plain", new byte[]{1});
        assertThat(service.valider(trompeur)).isEqualTo(FormatDocument.DOCX);
    }

    @Test
    void valider_refuseUnFormatInconnu() {
        assertThatThrownBy(() -> service.valider(fichier("texte.odt")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("non conforme");
    }

    @Test
    void valider_refuseUnFichierSansExtension() {
        assertThatThrownBy(() -> service.valider(fichier("conclusions")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("extension");
    }

    /** Le DJ peut retirer Word de la liste : le refus le dit alors, et ne parle pas d'un inconnu. */
    @Test
    void valider_refuseUnFormatDesactiveParLaConfiguration() {
        when(configurationService.getOrThrow("FORMATS_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("PDF,PNG,JPG").build());

        assertThatThrownBy(() -> service.valider(fichier("conclusions.docx")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("désactivé");
    }

    @Test
    void valider_refuseUnFichierTropLourd() {
        when(configurationService.getOrThrow("TAILLE_MAX_GED"))
                .thenReturn(ConfigurationSysteme.builder().valeur("2").build());

        assertThatThrownBy(() -> service.valider(fichier("conclusions.docx")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("supérieur au maximum");
    }
}
