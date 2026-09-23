package com.afriland.audiences.delibere.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DelaiRecoursSchedulerTest {

    @Mock private DelibereRepository delibereRepository;
    @Mock private EtapeProcedureRepository etapeProcedureRepository;
    @Mock private DossierJuristeRepository dossierJuristeRepository;
    @Mock private AlerteService alerteService;

    private DelaiRecoursScheduler scheduler;
    private Dossier dossier;
    private EtapeProcedure etapeVide;
    private Delibere delibere;

    @Mock
    private DossierAvocatRepository dossierAvocatRepository;

    @Mock
    private NotificationPrestataireService notificationPrestataireService;

    @BeforeEach
    void setUp() {
        scheduler = new DelaiRecoursScheduler(delibereRepository, etapeProcedureRepository, dossierJuristeRepository,
                dossierAvocatRepository, alerteService, notificationPrestataireService);
        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        etapeVide = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE)
                .statutCycleVie(StatutCycleVie.DELIBERE_VIDE).build();
        delibere = Delibere.builder().id(100L).dossier(dossier).etape(etapeVide)
                .dateEcheanceRecours(LocalDateTime.now().minusDays(1)).build();
    }

    @Test
    void verifier_signalsJuristesAndClotureEtape() {
        Utilisateur juriste = Utilisateur.builder().id(30L).build();
        DossierJuriste affectation = DossierJuriste.builder().dossier(dossier).utilisateur(juriste).build();
        when(delibereRepository.findByDateEcheanceRecoursNotNullAndDateEcheanceRecoursLessThanEqualAndEtapeStatutCycleVie(
                any(), eq(StatutCycleVie.DELIBERE_VIDE))).thenReturn(List.of(delibere));
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(List.of(affectation));
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.DELAI_RECOURS), eq(30L), any())).thenReturn(false);

        scheduler.verifier();

        verify(alerteService).declencher(eq(TypeAlerte.DELAI_RECOURS), eq(1L), eq(juriste), any());
        assertThat(etapeVide.getStatutCycleVie()).isEqualTo(StatutCycleVie.CLOTURE);
        verify(etapeProcedureRepository).save(etapeVide);
    }

    @Test
    void verifier_doesNotResignalAlreadyAlertedJuriste() {
        Utilisateur juriste = Utilisateur.builder().id(30L).build();
        DossierJuriste affectation = DossierJuriste.builder().dossier(dossier).utilisateur(juriste).build();
        when(delibereRepository.findByDateEcheanceRecoursNotNullAndDateEcheanceRecoursLessThanEqualAndEtapeStatutCycleVie(
                any(), eq(StatutCycleVie.DELIBERE_VIDE))).thenReturn(List.of(delibere));
        when(dossierJuristeRepository.findByDossierId(1L)).thenReturn(List.of(affectation));
        when(alerteService.aDejaEteDeclenchee(eq(TypeAlerte.DELAI_RECOURS), eq(30L), any())).thenReturn(true);

        scheduler.verifier();

        verify(alerteService, never()).declencher(any(), any(), any(), any());
        assertThat(etapeVide.getStatutCycleVie()).isEqualTo(StatutCycleVie.CLOTURE);
    }
}
