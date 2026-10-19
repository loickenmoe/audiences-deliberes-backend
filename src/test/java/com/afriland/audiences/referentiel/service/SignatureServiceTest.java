package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The stored signature: <b>each user uploads their own, and nobody else's</b>. The service never
 * takes an identifier to write, it works on the session. It refuses what the PDF could not apply,
 * rather than producing an unsigned letter noticed only when printed.
 */
@ExtendWith(MockitoExtension.class)
class SignatureServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private CurrentUserService currentUserService;

    private SignatureService service;
    private Utilisateur moi;

    /** A tiny but valid PNG: the service does not decode what it receives. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @BeforeEach
    void setUp() {
        service = new SignatureService(utilisateurRepository, currentUserService);
        moi = Utilisateur.builder().id(7L).nom("Mbarga").prenom("Awa").build();
    }

    private void jeSuisConnecte() {
        when(currentUserService.getCurrentUser()).thenReturn(moi);
        when(utilisateurRepository.findById(7L)).thenReturn(Optional.of(moi));
    }

    @Test
    void deposer_enregistreLImageSonTypeEtSaDate() {
        jeSuisConnecte();
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(appel -> appel.getArgument(0));

        var deposee = service.deposer(new MockMultipartFile("fichier", "sig.png", "image/png", PNG));

        assertThat(deposee.image()).isEqualTo(PNG);
        assertThat(deposee.type()).isEqualTo("image/png");
        assertThat(deposee.deposeeLe()).isNotNull();
        assertThat(moi.getSignature()).isEqualTo(PNG);
    }

    /**
     * PDFBox reads PNG and JPEG. An SVG or PDF would produce a letter <b>without</b> signature, noticed
     * only when printed: refusing beats silence.
     */
    @Test
    void deposer_refuseUnFormatQueLaLettreNeSauraitApposer() {
        assertThatThrownBy(() -> service.deposer(
                new MockMultipartFile("fichier", "sig.svg", "image/svg+xml", PNG)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("PNG ou en JPEG");

        verify(utilisateurRepository, never()).save(any());
    }

    /** Two hundred thousand bytes: ample for a scanned signature, tight for a photograph. */
    @Test
    void deposer_refuseUneImageTropLourde() {
        byte[] tropGros = new byte[200_001];

        assertThatThrownBy(() -> service.deposer(
                new MockMultipartFile("fichier", "sig.png", "image/png", tropGros)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("200 Ko");

        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void deposer_refuseUnFichierVide() {
        assertThatThrownBy(() -> service.deposer(
                new MockMultipartFile("fichier", "sig.png", "image/png", new byte[0])))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Aucun fichier");
    }

    /** Nothing uploaded: absence is not an error, the letter goes out with the name only. */
    @Test
    void mienne_estVideTantQueRienNEstDepose() {
        jeSuisConnecte();

        assertThat(service.mienne()).isEmpty();
    }

    @Test
    void mienne_renvoieCeQuiAEteDepose() {
        moi.setSignature(PNG);
        moi.setSignatureType("image/png");
        jeSuisConnecte();

        assertThat(service.mienne()).isPresent().get()
                .satisfies(signature -> assertThat(signature.type()).isEqualTo("image/png"));
    }

    /**
     * Removal does not erase already signed letters: their PDF is generated and archived. Nor does it
     * erase any proof: the image was never the proof.
     */
    @Test
    void retirer_videLesTroisChamps() {
        moi.setSignature(PNG);
        moi.setSignatureType("image/png");
        moi.setSignatureDeposeeLe(java.time.LocalDateTime.now());
        jeSuisConnecte();
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(appel -> appel.getArgument(0));

        service.retirer();

        assertThat(moi.getSignature()).isNull();
        assertThat(moi.getSignatureType()).isNull();
        assertThat(moi.getSignatureDeposeeLe()).isNull();
    }

    /**
     * {@code pour} serves internal calls (applying the signature to a letter). No endpoint exposes it:
     * returning someone else's signature would let it be applied elsewhere.
     */
    @Test
    void pour_litLaSignatureDUnAutreSansPasserParLaSession() {
        Utilisateur sh = Utilisateur.builder().id(9L).build();
        sh.setSignature(PNG);
        sh.setSignatureType("image/png");
        when(utilisateurRepository.findById(9L)).thenReturn(Optional.of(sh));

        assertThat(service.pour(9L)).isPresent();
        verify(currentUserService, never()).getCurrentUser();
    }

    @Test
    void pour_toleUnIdentifiantAbsent() {
        assertThat(service.pour(null)).isEmpty();
        verify(utilisateurRepository, never()).findById(any());
    }
}
