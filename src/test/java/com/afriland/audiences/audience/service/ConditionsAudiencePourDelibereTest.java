package com.afriland.audiences.audience.service;

import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.audience.service.ConditionsAudiencePourDelibere.AucuneAudienceEnAttente;
import com.afriland.audiences.audience.service.ConditionsAudiencePourDelibere.AudienceTenueRequise;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The two conditions a step must meet before going to deliberation: a hearing has been held, and
 * no scheduled hearing is left behind (it would keep feeding the calendar and reminders).
 */
@ExtendWith(MockitoExtension.class)
class ConditionsAudiencePourDelibereTest {

    @Mock private AudienceRepository audienceRepository;

    private AudienceTenueRequise audienceTenue;
    private AucuneAudienceEnAttente aucuneEnAttente;
    private EtapeProcedure etape;

    @BeforeEach
    void setUp() {
        audienceTenue = new AudienceTenueRequise(audienceRepository);
        aucuneEnAttente = new AucuneAudienceEnAttente(audienceRepository);
        etape = EtapeProcedure.builder().id(7L).build();
    }

    // ------------------------------------------------------------------ Scope

    @Test
    void les_deux_conditions_ne_valent_que_pour_la_mise_en_delibere() {
        for (var condition : java.util.List.of(audienceTenue, aucuneEnAttente)) {
            assertThat(condition.concerne(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE)).isTrue();

            // Other transitions are not concerned: closure or appeal requires no hearing, and applying this
            // rule to them would block the lifecycle.
            assertThat(condition.concerne(StatutCycleVie.OUVERTURE, StatutCycleVie.EN_COURS)).isFalse();
            assertThat(condition.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.CLOTURE)).isFalse();
            assertThat(condition.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.EN_COURS)).isFalse();
            assertThat(condition.concerne(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE)).isFalse();
        }
    }

    // ------------------------------------------------------------------ A hearing held

    @Test
    void sans_audience_tenue_la_mise_en_delibere_est_refusee() {
        when(audienceRepository.existsByEtapeIdAndStatut(7L, StatutAudience.TENUE)).thenReturn(false);

        assertThat(audienceTenue.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .contains("Aucune audience tenue")
                        .contains("compte rendu"));
    }

    /**
     * "Held" means "reported": the status is only set when the report is entered. This condition
     * checks that status, not a past date.
     */
    @Test
    void une_audience_tenue_suffit() {
        when(audienceRepository.existsByEtapeIdAndStatut(7L, StatutAudience.TENUE)).thenReturn(true);

        assertThat(audienceTenue.empechement(etape)).isEmpty();
    }

    @Test
    void seul_le_statut_TENUE_compte() {
        when(audienceRepository.existsByEtapeIdAndStatut(anyLong(), any())).thenReturn(false);

        audienceTenue.empechement(etape);

        // A cancelled or merely scheduled hearing does not count as held: the condition only checks
        // TENUE, never "a hearing exists".
        org.mockito.Mockito.verify(audienceRepository).existsByEtapeIdAndStatut(7L, StatutAudience.TENUE);
        org.mockito.Mockito.verifyNoMoreInteractions(audienceRepository);
    }

    // ------------------------------------------------------------------ No pending hearing

    @Test
    void une_audience_encore_planifiee_bloque_la_mise_en_delibere() {
        when(audienceRepository.countByEtapeIdAndStatut(7L, StatutAudience.PLANIFIEE)).thenReturn(1L);

        assertThat(aucuneEnAttente.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .startsWith("Une audience reste planifiée")
                        .contains("tenez-la ou annulez-la"));
    }

    /** The count is stated: "some hearings" would leave the user wondering how many and which. */
    @Test
    void plusieurs_audiences_en_attente_sont_comptees_et_accordees() {
        when(audienceRepository.countByEtapeIdAndStatut(7L, StatutAudience.PLANIFIEE)).thenReturn(3L);

        assertThat(aucuneEnAttente.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .startsWith("3 audiences restent planifiées")
                        .contains("tenez-les ou annulez-les"));
    }

    @Test
    void aucune_audience_en_attente_laisse_passer() {
        when(audienceRepository.countByEtapeIdAndStatut(7L, StatutAudience.PLANIFIEE)).thenReturn(0L);

        assertThat(aucuneEnAttente.empechement(etape)).isEmpty();
    }

    /**
     * The target case: one hearing was held, another is still scheduled. The first condition is met,
     * the second is not, and it is the second one that must report.
     */
    @Test
    void une_audience_tenue_ne_rachete_pas_une_audience_encore_planifiee() {
        when(audienceRepository.existsByEtapeIdAndStatut(7L, StatutAudience.TENUE)).thenReturn(true);
        when(audienceRepository.countByEtapeIdAndStatut(7L, StatutAudience.PLANIFIEE)).thenReturn(1L);

        assertThat(audienceTenue.empechement(etape)).isEmpty();
        assertThat(aucuneEnAttente.empechement(etape)).isPresent();
    }
}
