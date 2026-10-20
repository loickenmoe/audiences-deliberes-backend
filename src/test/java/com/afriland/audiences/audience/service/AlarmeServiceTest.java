package com.afriland.audiences.audience.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.audience.domain.Alarme;
import com.afriland.audiences.audience.domain.StatutAlarme;
import com.afriland.audiences.audience.mapper.AlarmeMapper;
import com.afriland.audiences.audience.repository.AlarmeRepository;
import com.afriland.audiences.audience.web.dto.AlarmeResponse;
import com.afriland.audiences.audience.web.dto.CreerAlarmeRequest;
import com.afriland.audiences.audience.web.dto.ReprogrammerAlarmeRequest;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmeServiceTest {

    @Mock private AlarmeRepository alarmeRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private EtapeProcedureRepository etapeProcedureRepository;
    @Mock private HistoriqueActionService historiqueActionService;
    @Mock private CurrentUserService currentUserService;

    private final AlarmeMapper alarmeMapper = alarme -> new AlarmeResponse(
            alarme.getId(), alarme.getDossier().getId(), alarme.getEtape() != null ? alarme.getEtape().getId() : null,
            alarme.getObjet(), alarme.getDateEcheance(), alarme.getStatut(), alarme.getDateTraitement(),
            alarme.getAlarmePrecedente() != null ? alarme.getAlarmePrecedente().getId() : null,
            alarme.getCreatedBy().getId(), alarme.getCreatedAt());

    private AlarmeService service;
    private Dossier dossier;
    private EtapeProcedure etape;
    private Utilisateur juriste;

    @BeforeEach
    void setUp() {
        service = new AlarmeService(alarmeRepository, dossierRepository, etapeProcedureRepository,
                historiqueActionService, currentUserService, alarmeMapper);
        dossier = Dossier.builder().id(1L).reference("DOS-2026-001").build();
        etape = EtapeProcedure.builder().id(10L).dossier(dossier).type(TypeEtape.INSTANCE).build();
        juriste = Utilisateur.builder().id(7L).build();
    }

    @Test
    void creerAlarme_rejectsPastEcheance() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etape));

        var requete = new CreerAlarmeRequest(10L, "Relancer le client", LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.creerAlarme(1L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("future");
    }

    @Test
    void creerAlarme_nominalCreatesActiveAlarme() {
        when(dossierRepository.findById(1L)).thenReturn(Optional.of(dossier));
        when(etapeProcedureRepository.findById(10L)).thenReturn(Optional.of(etape));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(alarmeRepository.save(any(Alarme.class))).thenAnswer(inv -> {
            Alarme a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(100L);
            }
            return a;
        });

        AlarmeResponse result = service.creerAlarme(1L,
                new CreerAlarmeRequest(10L, "Relancer le client", LocalDateTime.now().plusDays(3)));

        assertThat(result.statut()).isEqualTo(StatutAlarme.ACTIVE);
        assertThat(result.objet()).isEqualTo("Relancer le client");
        assertThat(result.createdBy()).isEqualTo(7L);
    }

    @Test
    void reprogrammerAlarme_rejectsWhenNotActive() {
        Alarme traitee = Alarme.builder().id(3L).dossier(dossier).objet("x")
                .dateEcheance(LocalDateTime.now().minusDays(1)).statut(StatutAlarme.TRAITEE).createdBy(juriste).build();
        when(alarmeRepository.findById(3L)).thenReturn(Optional.of(traitee));

        var requete = new ReprogrammerAlarmeRequest(null, LocalDateTime.now().plusDays(2));
        assertThatThrownBy(() -> service.reprogrammerAlarme(3L, requete))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà été reprogrammée");
    }

    @Test
    void reprogrammerAlarme_marksOldTraiteeAndCreatesNewLinkedAlarme() {
        Alarme ancienne = Alarme.builder().id(3L).dossier(dossier).etape(etape).objet("Relancer")
                .dateEcheance(LocalDateTime.now().minusHours(1)).statut(StatutAlarme.ACTIVE).createdBy(juriste).build();
        when(alarmeRepository.findById(3L)).thenReturn(Optional.of(ancienne));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(alarmeRepository.save(any(Alarme.class))).thenAnswer(inv -> {
            Alarme a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(101L);
            }
            return a;
        });

        AlarmeResponse result = service.reprogrammerAlarme(3L,
                new ReprogrammerAlarmeRequest(null, LocalDateTime.now().plusDays(5)));

        assertThat(ancienne.getStatut()).isEqualTo(StatutAlarme.TRAITEE);
        assertThat(ancienne.getDateTraitement()).isNotNull();
        assertThat(result.statut()).isEqualTo(StatutAlarme.ACTIVE);
        assertThat(result.objet()).isEqualTo("Relancer");
        assertThat(result.alarmePrecedenteId()).isEqualTo(3L);
    }

    /** Closes a handled alarm without replacing it. */
    @Test
    void traiterAlarme_passeTraiteeSansCreerDeNouvelleAlarme() {
        Alarme active = Alarme.builder().id(3L).dossier(dossier).etape(etape).objet("Relancer le greffe")
                .dateEcheance(LocalDateTime.now().minusHours(2)).statut(StatutAlarme.ACTIVE).createdBy(juriste).build();
        when(alarmeRepository.findById(3L)).thenReturn(Optional.of(active));
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(alarmeRepository.save(any(Alarme.class))).thenAnswer(inv -> inv.getArgument(0));

        AlarmeResponse result = service.traiterAlarme(3L);

        assertThat(result.id()).isEqualTo(3L);
        assertThat(result.statut()).isEqualTo(StatutAlarme.TRAITEE);
        assertThat(result.dateTraitement()).isNotNull();
        // A single save: the alarm itself, no replacement.
        verify(alarmeRepository, times(1)).save(any(Alarme.class));
        verify(historiqueActionService).enregistrerPour(eq(juriste), eq(1L), eq(TypeActionHistorique.ALARME_TRAITEE), any());
    }

    @Test
    void traiterAlarme_refuseUneAlarmeDejaClose() {
        Alarme traitee = Alarme.builder().id(3L).dossier(dossier).objet("x")
                .dateEcheance(LocalDateTime.now().minusDays(1)).statut(StatutAlarme.TRAITEE).createdBy(juriste).build();
        when(alarmeRepository.findById(3L)).thenReturn(Optional.of(traitee));

        assertThatThrownBy(() -> service.traiterAlarme(3L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("déjà été reprogrammée ou traitée");
        verify(alarmeRepository, never()).save(any());
    }

    /** Case file "alarms" tab. */
    @Test
    void listerParDossier_retourneLesAlarmesTrieesParEcheance() {
        when(dossierRepository.existsById(1L)).thenReturn(true);
        when(alarmeRepository.findByDossierIdOrderByDateEcheanceAsc(eq(1L), any())).thenReturn(new PageImpl<>(java.util.List.of(
                Alarme.builder().id(1L).dossier(dossier).objet("Proche").statut(StatutAlarme.ACTIVE)
                        .dateEcheance(LocalDateTime.now().plusDays(1)).createdBy(juriste).build(),
                Alarme.builder().id(2L).dossier(dossier).objet("Lointaine").statut(StatutAlarme.ACTIVE)
                        .dateEcheance(LocalDateTime.now().plusDays(9)).createdBy(juriste).build())));

        assertThat(service.listerParDossier(1L, PageRequest.of(0, 10)).content())
                .extracting(AlarmeResponse::objet)
                .containsExactly("Proche", "Lointaine");
    }

    @Test
    void listerParDossier_dossierInconnu_retourne404() {
        when(dossierRepository.existsById(999L)).thenReturn(false);

        var pageRequest = PageRequest.of(0, 10);
        assertThatThrownBy(() -> service.listerParDossier(999L, pageRequest))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Dossier introuvable");
    }
}
