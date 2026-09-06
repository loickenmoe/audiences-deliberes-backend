package com.afriland.audiences.audience.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.domain.DossierJuristeId;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RappelAudienceSchedulerTest {

    @Mock private AudienceRepository audienceRepository;
    @Mock private DossierJuristeRepository dossierJuristeRepository;
    @Mock private AlerteService alerteService;

    @InjectMocks
    private RappelAudienceScheduler scheduler;

    @Test
    void verifier_signalsEachJuristeForJ7J3J1Audiences() {
        Dossier dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        Audience audienceJ7 = Audience.builder().id(5L).dossier(dossier)
                .datePlanifiee(LocalDate.now().plusDays(7)).statut(StatutAudience.PLANIFIEE).build();
        Utilisateur juriste = Utilisateur.builder().id(9L).build();
        DossierJuriste affectation = DossierJuriste.builder()
                .id(new DossierJuristeId(1L, 9L)).dossier(dossier).utilisateur(juriste).build();

        when(audienceRepository.findByStatutAndDatePlanifiee(StatutAudience.PLANIFIEE, LocalDate.now().plusDays(7)))
                .thenReturn(List.of(audienceJ7));
        when(audienceRepository.findByStatutAndDatePlanifiee(StatutAudience.PLANIFIEE, LocalDate.now().plusDays(3)))
                .thenReturn(List.of());
        when(audienceRepository.findByStatutAndDatePlanifiee(StatutAudience.PLANIFIEE, LocalDate.now().plusDays(1)))
                .thenReturn(List.of());
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(List.of(affectation));
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.RAPPEL_AUDIENCE), eq(9L), any())).thenReturn(false);

        scheduler.verifier();

        verify(alerteService).declencher(eq(TypeAlerte.RAPPEL_AUDIENCE), eq(1L), eq(juriste), any());
    }

    @Test
    void verifier_skipsJuristeAlreadyNotifiedForThisReminder() {
        Dossier dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        Audience audienceJ1 = Audience.builder().id(5L).dossier(dossier)
                .datePlanifiee(LocalDate.now().plusDays(1)).statut(StatutAudience.PLANIFIEE).build();
        Utilisateur juriste = Utilisateur.builder().id(9L).build();
        DossierJuriste affectation = DossierJuriste.builder()
                .id(new DossierJuristeId(1L, 9L)).dossier(dossier).utilisateur(juriste).build();

        when(audienceRepository.findByStatutAndDatePlanifiee(StatutAudience.PLANIFIEE, LocalDate.now().plusDays(7)))
                .thenReturn(List.of());
        when(audienceRepository.findByStatutAndDatePlanifiee(StatutAudience.PLANIFIEE, LocalDate.now().plusDays(3)))
                .thenReturn(List.of());
        when(audienceRepository.findByStatutAndDatePlanifiee(StatutAudience.PLANIFIEE, LocalDate.now().plusDays(1)))
                .thenReturn(List.of(audienceJ1));
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(List.of(affectation));
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.RAPPEL_AUDIENCE), eq(9L), any())).thenReturn(true);

        scheduler.verifier();

        verify(alerteService, never()).declencher(any(), any(), any(), any());
    }
}
