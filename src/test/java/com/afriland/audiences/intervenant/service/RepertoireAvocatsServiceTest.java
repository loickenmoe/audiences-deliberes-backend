package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.intervenant.web.dto.AvocatRepertoireResponse;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepertoireAvocatsServiceTest {

    @Mock private AvocatRepository avocatRepository;
    @Mock private DossierAvocatRepository dossierAvocatRepository;
    @Mock private DelibereRepository delibereRepository;

    private RepertoireAvocatsService service;

    @BeforeEach
    void setUp() {
        service = new RepertoireAvocatsService(avocatRepository, dossierAvocatRepository, delibereRepository);
    }

    @Test
    void lister_returnsZeroWhenNoData() {
        Avocat avocat = Avocat.builder().id(1L).nom("Me Fotso").build();
        when(avocatRepository.findAll()).thenReturn(List.of(avocat));
        when(dossierAvocatRepository.compterDossiersActifsParAvocat(any())).thenReturn(List.of());
        when(delibereRepository.calculerPerformanceParAvocat()).thenReturn(List.of());

        List<AvocatRepertoireResponse> result = service.lister(null, PageRequest.of(0, 10)).content();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).indicateurCharge()).isZero();
        assertThat(result.get(0).indicateurPerformance()).isZero();
        /*
         * **Zéro sur zéro, et non zéro tout court.** Sans délibéré il n'y a rien à mesurer ; c'est
         * le total qui le dit, et qui permet à l'écran d'écrire « aucun délibéré rendu » plutôt
         * qu'un chiffre qui se lirait comme une contre-performance.
         */
        assertThat(result.get(0).deliberesFavorables()).isZero();
        assertThat(result.get(0).deliberesTotal()).isZero();
    }

    @Test
    void lister_computesChargeAndPerformance() {
        Avocat avocat = Avocat.builder().id(1L).nom("Me Fotso").build();
        when(avocatRepository.findAll()).thenReturn(List.of(avocat));
        when(dossierAvocatRepository.compterDossiersActifsParAvocat(any())).thenReturn(List.of(charge(1L, 3L)));
        when(delibereRepository.calculerPerformanceParAvocat()).thenReturn(List.of(performance(1L, 3L, 4L)));

        List<AvocatRepertoireResponse> result = service.lister(null, PageRequest.of(0, 10)).content();

        assertThat(result.get(0).indicateurCharge()).isEqualTo(3L);
        /*
         * Les deux décomptes sont ce que l'écran montre depuis le 2026-10-06 : « 3 sur 4 » dit ce
         * que « 75 % » résumait, **et sur combien d'affaires**. Le taux reste calculé - le contrat
         * le porte toujours - mais il n'est plus ce qu'on lit.
         */
        assertThat(result.get(0).deliberesFavorables()).isEqualTo(3L);
        assertThat(result.get(0).deliberesTotal()).isEqualTo(4L);
        assertThat(result.get(0).indicateurPerformance()).isEqualTo(0.75d);
    }

    @Test
    void lister_sortsByChargeAscending() {
        Avocat avocatCharge = Avocat.builder().id(1L).nom("Charge élevée").build();
        Avocat avocatLeger = Avocat.builder().id(2L).nom("Charge faible").build();
        when(avocatRepository.findAll()).thenReturn(List.of(avocatCharge, avocatLeger));
        when(dossierAvocatRepository.compterDossiersActifsParAvocat(any())).thenReturn(List.of(charge(1L, 10L), charge(2L, 1L)));
        when(delibereRepository.calculerPerformanceParAvocat()).thenReturn(List.of());

        List<AvocatRepertoireResponse> result = service.lister(null, PageRequest.of(0, 10)).content();

        assertThat(result).extracting(AvocatRepertoireResponse::id).containsExactly(2L, 1L);
    }

    @Test
    void lister_filtersByNom() {
        when(avocatRepository.findByNomContainingIgnoreCase("Fotso")).thenReturn(List.of(Avocat.builder().id(1L).nom("Me Fotso").build()));
        when(dossierAvocatRepository.compterDossiersActifsParAvocat(any())).thenReturn(List.of());
        when(delibereRepository.calculerPerformanceParAvocat()).thenReturn(List.of());

        List<AvocatRepertoireResponse> result = service.lister("Fotso", PageRequest.of(0, 10)).content();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nom()).isEqualTo("Me Fotso");
    }

    private DossierAvocatRepository.ChargeAvocat charge(Long avocatId, Long total) {
        return new DossierAvocatRepository.ChargeAvocat() {
            @Override public Long getAvocatId() { return avocatId; }
            @Override public Long getTotal() { return total; }
        };
    }

    private DelibereRepository.PerformanceAvocat performance(Long avocatId, Long favorables, Long total) {
        return new DelibereRepository.PerformanceAvocat() {
            @Override public Long getAvocatId() { return avocatId; }
            @Override public Long getFavorables() { return favorables; }
            @Override public Long getTotal() { return total; }
        };
    }
}
