package com.afriland.audiences.frais.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.frais.domain.DecisionValidationFrais;
import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.EtapeValidationFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.domain.ValidationFrais;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.frais.repository.ValidationFraisRepository;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraisRelanceSchedulerTest {

    @Mock private DemandeFraisRepository demandeFraisRepository;
    @Mock private ValidationFraisRepository validationFraisRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private AlerteService alerteService;
    @Mock private ConfigurationService configurationService;

    private FraisRelanceScheduler scheduler;
    private DemandeFrais demande;
    private final Utilisateur dj = Utilisateur.builder().id(40L).build();

    @BeforeEach
    void setUp() {
        scheduler = new FraisRelanceScheduler(demandeFraisRepository, validationFraisRepository,
                utilisateurRepository, alerteService, configurationService);
        Dossier dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        demande = DemandeFrais.builder().id(100L).dossier(dossier).statutCircuit(StatutCircuitFrais.OPPORTUNITE).build();
        lenient().when(configurationService.getOrThrow("DELAI_VALIDATION_CONJOINTE"))
                .thenReturn(ConfigurationSysteme.builder().cle("DELAI_VALIDATION_CONJOINTE").valeur("5").build());
    }

    private static ValidationFrais vote(EtapeValidationFrais etape, LocalDateTime date) {
        return ValidationFrais.builder().etape(etape).decision(DecisionValidationFrais.ACCORD).dateValidation(date).build();
    }

    @Test
    void verifier_relaunchesOnlyTheApproverWhoHasNotVoted() {
        LocalDateTime maintenant = LocalDateTime.now();
        when(demandeFraisRepository.findAllByStatutCircuit(StatutCircuitFrais.OPPORTUNITE)).thenReturn(List.of(demande));
        when(validationFraisRepository.findByDemandeFraisIdOrderByDateValidationAscIdAsc(100L)).thenReturn(List.of(
                vote(EtapeValidationFrais.OPPORTUNITE, maintenant.minusDays(6)),
                vote(EtapeValidationFrais.CONJOINTE_DJA, maintenant.minusDays(1))));
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(dj));

        scheduler.verifier();

        verify(alerteService).declencher(eq(TypeAlerte.VALIDATION_CONJOINTE_REQUISE), eq(1L), eq(dj), any());
        verify(utilisateurRepository, never()).findByProfilCodeAndActifTrue("DJA");
        assertThat(demande.getDateDerniereRelance()).isNotNull();
        verify(demandeFraisRepository).save(demande);
    }

    @Test
    void verifier_waitsForTheConfiguredDelay() {
        when(demandeFraisRepository.findAllByStatutCircuit(StatutCircuitFrais.OPPORTUNITE)).thenReturn(List.of(demande));
        when(validationFraisRepository.findByDemandeFraisIdOrderByDateValidationAscIdAsc(100L)).thenReturn(List.of(
                vote(EtapeValidationFrais.OPPORTUNITE, LocalDateTime.now().minusDays(2))));

        scheduler.verifier();

        verify(alerteService, never()).declencher(any(), any(), any(), any());
        verify(demandeFraisRepository, never()).save(any());
    }

    @Test
    void verifier_countsFromTheLastRelance() {
        demande.setDateDerniereRelance(LocalDateTime.now().minusDays(1));
        when(demandeFraisRepository.findAllByStatutCircuit(StatutCircuitFrais.OPPORTUNITE)).thenReturn(List.of(demande));
        when(validationFraisRepository.findByDemandeFraisIdOrderByDateValidationAscIdAsc(100L)).thenReturn(List.of(
                vote(EtapeValidationFrais.OPPORTUNITE, LocalDateTime.now().minusDays(10))));

        scheduler.verifier();

        verify(alerteService, never()).declencher(any(), any(), any(), any());
    }

    @Test
    void verifier_noOpWhenNothingPending() {
        when(demandeFraisRepository.findAllByStatutCircuit(StatutCircuitFrais.OPPORTUNITE)).thenReturn(List.of());

        scheduler.verifier();

        verify(configurationService, never()).getOrThrow(anyString());
        verify(alerteService, never()).declencher(any(), any(), any(), any());
    }
}
