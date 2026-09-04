package com.afriland.audiences.dossier.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository.ChargeAvocat;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargeMaxAvocatSchedulerTest {

    @Mock
    private DossierAvocatRepository dossierAvocatRepository;

    @Mock
    private AvocatRepository avocatRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private AlerteService alerteService;

    @InjectMocks
    private ChargeMaxAvocatScheduler scheduler;

    private static ChargeAvocat charge(Long avocatId, long total) {
        return new ChargeAvocat() {
            public Long getAvocatId() {
                return avocatId;
            }

            public Long getTotal() {
                return total;
            }
        };
    }

    @Test
    void verifier_signalsDjWhenAvocatExceedsThresholdAndNotAlreadySignaled() {
        Utilisateur dj = Utilisateur.builder().id(1L).build();
        Avocat avocat = Avocat.builder().id(9L).nom("Me Fotso").build();
        when(configurationService.getOrThrow("CHARGE_MAX_AVOCAT"))
                .thenReturn(ConfigurationSysteme.builder().cle("CHARGE_MAX_AVOCAT").valeur("20").build());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(dj));
        when(dossierAvocatRepository.compterDossiersParAvocat()).thenReturn(List.of(charge(9L, 21L)));
        when(avocatRepository.findById(9L)).thenReturn(Optional.of(avocat));
        when(alerteService.estDejaSignalee(eq(TypeAlerte.CHARGE_MAX_AVOCAT), eq(1L), any())).thenReturn(false);

        scheduler.verifier();

        verify(alerteService).declencher(eq(TypeAlerte.CHARGE_MAX_AVOCAT), eq((Long) null), eq(dj), any());
    }

    @Test
    void verifier_skipsAvocatAlreadySignaled() {
        Utilisateur dj = Utilisateur.builder().id(1L).build();
        Avocat avocat = Avocat.builder().id(9L).nom("Me Fotso").build();
        when(configurationService.getOrThrow("CHARGE_MAX_AVOCAT"))
                .thenReturn(ConfigurationSysteme.builder().cle("CHARGE_MAX_AVOCAT").valeur("20").build());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(dj));
        when(dossierAvocatRepository.compterDossiersParAvocat()).thenReturn(List.of(charge(9L, 21L)));
        when(avocatRepository.findById(9L)).thenReturn(Optional.of(avocat));
        when(alerteService.estDejaSignalee(eq(TypeAlerte.CHARGE_MAX_AVOCAT), eq(1L), any())).thenReturn(true);

        scheduler.verifier();

        verify(alerteService, never()).declencher(any(), any(), any(), any());
    }

    @Test
    void verifier_ignoresAvocatsAtOrBelowThreshold() {
        when(configurationService.getOrThrow("CHARGE_MAX_AVOCAT"))
                .thenReturn(ConfigurationSysteme.builder().cle("CHARGE_MAX_AVOCAT").valeur("20").build());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of(Utilisateur.builder().id(1L).build()));
        when(dossierAvocatRepository.compterDossiersParAvocat()).thenReturn(List.of(charge(9L, 20L)));

        scheduler.verifier();

        verify(avocatRepository, never()).findById(anyLong());
        verify(alerteService, never()).declencher(any(), any(), any(), any());
    }

    @Test
    void verifier_doesNothingWhenNoDjDestinataires() {
        lenient().when(configurationService.getOrThrow("CHARGE_MAX_AVOCAT"))
                .thenReturn(ConfigurationSysteme.builder().cle("CHARGE_MAX_AVOCAT").valeur("20").build());
        when(utilisateurRepository.findByProfilCodeAndActifTrue("DJ")).thenReturn(List.of());

        scheduler.verifier();

        verify(dossierAvocatRepository, never()).compterDossiersParAvocat();
    }

    @Test
    void verifier_ignoresNonNumericThresholdWithoutThrowing() {
        when(configurationService.getOrThrow("CHARGE_MAX_AVOCAT"))
                .thenReturn(ConfigurationSysteme.builder().cle("CHARGE_MAX_AVOCAT").valeur("non-numérique").build());

        scheduler.verifier();

        verify(utilisateurRepository, never()).findByProfilCodeAndActifTrue(any());
    }
}
