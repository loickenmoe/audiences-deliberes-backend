package com.afriland.audiences.audience.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Alarme;
import com.afriland.audiences.audience.domain.StatutAlarme;
import com.afriland.audiences.audience.repository.AlarmeRepository;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmeEcheanceSchedulerTest {

    @Mock private AlarmeRepository alarmeRepository;
    @Mock private AlerteService alerteService;

    @InjectMocks
    private AlarmeEcheanceScheduler scheduler;

    @Test
    void verifier_signalsCreatorForOverdueActiveAlarm() {
        Dossier dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        Utilisateur juriste = Utilisateur.builder().id(9L).build();
        Alarme alarme = Alarme.builder().id(4L).dossier(dossier).objet("Relancer le client")
                .dateEcheance(LocalDateTime.now().minusHours(1)).statut(StatutAlarme.ACTIVE).createdBy(juriste).build();

        when(alarmeRepository.findByStatutAndDateEcheanceLessThanEqual(eq(StatutAlarme.ACTIVE), any()))
                .thenReturn(List.of(alarme));
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.ALARME), eq(9L), any())).thenReturn(false);

        scheduler.verifier();

        verify(alerteService).declencher(eq(TypeAlerte.ALARME), eq(1L), eq(juriste), any());
    }

    @Test
    void verifier_skipsAlarmAlreadySignaledOnce() {
        Dossier dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        Utilisateur juriste = Utilisateur.builder().id(9L).build();
        Alarme alarme = Alarme.builder().id(4L).dossier(dossier).objet("Relancer le client")
                .dateEcheance(LocalDateTime.now().minusHours(1)).statut(StatutAlarme.ACTIVE).createdBy(juriste).build();

        when(alarmeRepository.findByStatutAndDateEcheanceLessThanEqual(eq(StatutAlarme.ACTIVE), any()))
                .thenReturn(List.of(alarme));
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.ALARME), eq(9L), any())).thenReturn(true);

        scheduler.verifier();

        verify(alerteService, never()).declencher(any(), any(), any(), any());
    }
}
