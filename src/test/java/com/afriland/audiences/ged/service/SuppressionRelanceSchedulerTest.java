package com.afriland.audiences.ged.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.ged.domain.DemandeSuppression;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.StatutSuppression;
import com.afriland.audiences.ged.repository.DemandeSuppressionRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuppressionRelanceSchedulerTest {

    @Mock private DemandeSuppressionRepository demandeSuppressionRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private AlerteService alerteService;

    private SuppressionRelanceScheduler scheduler;
    private Dossier dossier;
    private Document document;
    private DemandeSuppression demande;

    @BeforeEach
    void setUp() {
        scheduler = new SuppressionRelanceScheduler(demandeSuppressionRepository, utilisateurRepository, alerteService);
        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        document = Document.builder().id(5L).dossier(dossier).nomFichier("piece.pdf").build();
        demande = DemandeSuppression.builder().id(200L).document(document).statut(StatutSuppression.EN_ATTENTE).build();
    }

    @Test
    void verifier_relaunchesNeverRelancedDemandeAndUpdatesTimestamp() {
        Utilisateur dj = Utilisateur.builder().id(20L).build();
        when(demandeSuppressionRepository.findByStatutAndDateDerniereRelanceIsNullAndDateDemandeLessThanEqual(
                eq(StatutSuppression.EN_ATTENTE), any())).thenReturn(List.of(demande));
        when(demandeSuppressionRepository.findByStatutAndDateDerniereRelanceLessThanEqual(
                eq(StatutSuppression.EN_ATTENTE), any())).thenReturn(List.of());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(new java.util.ArrayList<>(List.of(dj)));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJA")).thenReturn(List.of());

        scheduler.verifier();

        verify(alerteService).declencher(eq(TypeAlerte.SUPPRESSION_DOCUMENT), eq(1L), eq(dj), any());
        assertThat(demande.getDateDerniereRelance()).isNotNull();
        verify(demandeSuppressionRepository).save(demande);
    }

    @Test
    void verifier_noOpWhenNothingPending() {
        when(demandeSuppressionRepository.findByStatutAndDateDerniereRelanceIsNullAndDateDemandeLessThanEqual(
                eq(StatutSuppression.EN_ATTENTE), any())).thenReturn(List.of());
        when(demandeSuppressionRepository.findByStatutAndDateDerniereRelanceLessThanEqual(
                eq(StatutSuppression.EN_ATTENTE), any())).thenReturn(List.of());

        scheduler.verifier();

        verify(alerteService, never()).declencher(any(), any(), any(), any());
        verify(demandeSuppressionRepository, never()).save(any());
    }
}
