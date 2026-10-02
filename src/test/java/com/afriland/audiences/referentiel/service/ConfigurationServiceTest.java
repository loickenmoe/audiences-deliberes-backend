package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.domain.TypeValeurConfiguration;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.mapper.ConfigurationMapper;
import com.afriland.audiences.referentiel.repository.ConfigurationSystemeRepository;
import com.afriland.audiences.referentiel.web.dto.ConfigurationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                new ConfigurationResponse("SEUIL_PROROGATIONS", "12", null, "ROLE_DJ", null, 3L,
                        TypeValeurConfiguration.ENTIER, 1L, 1_000L, List.of(), false));

        ConfigurationResponse result = configurationService.modifier("SEUIL_PROROGATIONS", "12");

        assertThat(config.getValeur()).isEqualTo("12");
        assertThat(result.valeur()).isEqualTo("12");
        // Q-111 : la clé n'est plus concaténée au libellé, elle est une donnée de `details`.
        verify(historiqueActionService).enregistrerPour(eq(dj), isNull(),
                eq(TypeActionHistorique.CONFIGURATION_MODIFIEE),
                eq(Map.of("cle", "SEUIL_PROROGATIONS", "ancienneValeur", "10", "nouvelleValeur", "12")));
    }

    /** Q-91 : une saisie non numérique est refusée ici, au lieu de casser le module frais plus tard. */
    @Test
    void modifier_rejectsValueThatBreaksTheKeyContract() {
        ConfigurationSysteme config = ConfigurationSysteme.builder().cle("SEUIL_MONTANT_DEFAUT").valeur("50000000").build();
        when(configurationSystemeRepository.findByCle("SEUIL_MONTANT_DEFAUT")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> configurationService.modifier("SEUIL_MONTANT_DEFAUT", "50 000 000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("SEUIL_MONTANT_DEFAUT");

        assertThat(config.getValeur()).isEqualTo("50000000");
        verifyNoInteractions(historiqueActionService);
    }

    /** Q-91 : la liste est enregistrée normalisée — GedService la découpe sans retirer les espaces. */
    @Test
    void modifier_storesNormalizedListValue() {
        ConfigurationSysteme config = ConfigurationSysteme.builder().cle("FORMATS_GED").valeur("PDF,PNG").build();
        when(configurationSystemeRepository.findByCle("FORMATS_GED")).thenReturn(Optional.of(config));
        when(configurationSystemeRepository.save(any(ConfigurationSysteme.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentUserService.getCurrentUser()).thenReturn(Utilisateur.builder().id(3L).build());
        when(configurationMapper.toResponse(any(ConfigurationSysteme.class))).thenReturn(
                new ConfigurationResponse("FORMATS_GED", "PDF,XLSX", null, "ROLE_DJ", null, 3L,
                        TypeValeurConfiguration.LISTE, null, null, List.of(), false));

        configurationService.modifier("FORMATS_GED", " pdf , xlsx ");

        assertThat(config.getValeur()).isEqualTo("PDF,XLSX");
    }

    /** Q-118 : ne rien envoyer par SMS est un réglage, pas une saisie manquante. */
    @Test
    void modifier_acceptsAnEmptyNotificationGrid() {
        ConfigurationSysteme config = ConfigurationSysteme.builder().cle("GRILLE_NOTIFICATION_SMS").valeur("RAPPEL_AUDIENCE").build();
        when(configurationSystemeRepository.findByCle("GRILLE_NOTIFICATION_SMS")).thenReturn(Optional.of(config));
        when(configurationSystemeRepository.save(any(ConfigurationSysteme.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentUserService.getCurrentUser()).thenReturn(Utilisateur.builder().id(3L).build());

        configurationService.modifier("GRILLE_NOTIFICATION_SMS", "");

        assertThat(config.getValeur()).isEmpty();
        verify(historiqueActionService).enregistrerPour(any(), isNull(),
                eq(TypeActionHistorique.CONFIGURATION_MODIFIEE),
                eq(Map.of("cle", "GRILLE_NOTIFICATION_SMS", "ancienneValeur", "RAPPEL_AUDIENCE", "nouvelleValeur", "")));
    }

    /** La requête n'exige plus qu'une valeur non nulle : une clé sans règle garde son refus du vide. */
    @Test
    void modifier_stillRejectsBlankValueForAKeyWithoutRule() {
        ConfigurationSysteme config = ConfigurationSysteme.builder().cle("CLE_SANS_REGLE").valeur("x").build();
        when(configurationSystemeRepository.findByCle("CLE_SANS_REGLE")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> configurationService.modifier("CLE_SANS_REGLE", "  "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("CLE_SANS_REGLE");
        assertThat(config.getValeur()).isEqualTo("x");
        verifyNoInteractions(historiqueActionService);
    }
}
