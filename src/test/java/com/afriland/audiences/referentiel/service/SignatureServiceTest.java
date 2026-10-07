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
 * La signature enregistrée (métier, 2026-10-03, observation 10 ; provenance arrêtée le 2026-10-06).
 *
 * <p>Ce que ces tests tiennent : <b>chacun dépose la sienne, et personne d'autre</b>. Le service ne
 * prend jamais d'identifiant pour écrire - il travaille sur la session. Et il refuse ce que le PDF
 * ne saurait apposer, plutôt que de produire une lettre sans signature dont le défaut ne se verrait
 * qu'à l'impression.
 */
@ExtendWith(MockitoExtension.class)
class SignatureServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private CurrentUserService currentUserService;

    private SignatureService service;
    private Utilisateur moi;

    /** Un PNG minuscule mais valide : ce que le service reçoit, il ne le décode pas. */
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
     * PDFBox lit le PNG et le JPEG. Un SVG ou un PDF produirait une lettre <b>sans</b> signature, et
     * le défaut ne se verrait qu'à l'impression : le refus vaut mieux que le silence.
     */
    @Test
    void deposer_refuseUnFormatQueLaLettreNeSauraitApposer() {
        assertThatThrownBy(() -> service.deposer(
                new MockMultipartFile("fichier", "sig.svg", "image/svg+xml", PNG)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("PNG ou en JPEG");

        verify(utilisateurRepository, never()).save(any());
    }

    /** Deux cent mille octets : large pour une signature scannée, étroit pour une photographie. */
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

    /** Rien n'a été déposé : l'absence n'est pas une erreur, la lettre part avec le nom seul. */
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
     * Le retrait n'efface pas les lettres déjà signées : leur PDF est produit et archivé. Et il
     * n'efface aucune preuve - la preuve n'a jamais été cette image.
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
     * {@code pour} sert les appels internes - apposer la signature sur une lettre. Il n'est exposé
     * par aucun endpoint : rendre la signature d'autrui permettrait de la récupérer pour l'apposer
     * ailleurs, ce que le dépôt réservé au titulaire cherche précisément à empêcher.
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
