package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.mapper.ConfigurationMapper;
import com.afriland.audiences.referentiel.repository.ConfigurationSystemeRepository;
import com.afriland.audiences.referentiel.web.dto.ConfigurationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    private ConfigurationSystemeRepository configurationSystemeRepository;

    @Mock
    private ConfigurationMapper configurationMapper;

    @Mock
    private HistoriqueActionService historiqueActionService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ConfigurationService configurationService;

    @Test
    void getOrThrow_throwsWhenKeyUnknown() {
        when(configurationSystemeRepository.findByCle("INEXISTANT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configurationService.getOrThrow("INEXISTANT")).isInstanceOf(ApiException.class);
    }

    @Test
    void modifier_updatesValueAndHistorizesOutsideAnyDossier() {
        ConfigurationSysteme config = ConfigurationSysteme.builder().cle("SEUIL_PROROGATIONS").valeur("10").build();
        Utilisateur dj = Utilisateur.builder().id(3L).build();
        when(configurationSystemeRepository.findByCle("SEUIL_PROROGATIONS")).thenReturn(Optional.of(config));
        when(configurationSystemeRepository.save(any(ConfigurationSysteme.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(configurationMapper.toResponse(any(ConfigurationSysteme.class))).thenReturn(
                new ConfigurationResponse("SEUIL_PROROGATIONS", "12", null, "ROLE_DJ", null));

        ConfigurationResponse result = configurationService.modifier("SEUIL_PROROGATIONS", "12");

        assertThat(config.getValeur()).isEqualTo("12");
        assertThat(result.valeur()).isEqualTo("12");
        verify(historiqueActionService).enregistrerPour(eq(dj), isNull(), eq("Modification de la configuration SEUIL_PROROGATIONS"),
                eq(Map.of("ancienneValeur", "10", "nouvelleValeur", "12")));
    }
}
