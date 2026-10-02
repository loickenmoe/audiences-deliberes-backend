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
 * Les deux conditions qu'une étape doit remplir pour partir en délibéré (métier, 2026-09-29).
 *
 * <p>Ce que ces tests protègent, ce sont deux refus qui n'existaient pas : jusqu'ici, une étape
 * partait en délibéré sans qu'aucune audience ne s'y soit tenue, et en laissant derrière elle des
 * audiences planifiées qui continuaient d'alimenter le calendrier et les rappels.
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

    // ------------------------------------------------------------------ Le champ d'application

    @Test
    void les_deux_conditions_ne_valent_que_pour_la_mise_en_delibere() {
        for (var condition : java.util.List.of(audienceTenue, aucuneEnAttente)) {
            assertThat(condition.concerne(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE)).isTrue();

            // Toutes les autres transitions ne les regardent pas : une clôture ou un recours
            // n'exigent pas d'audience, et les soumettre à cette règle bloquerait le cycle.
            assertThat(condition.concerne(StatutCycleVie.OUVERTURE, StatutCycleVie.EN_COURS)).isFalse();
            assertThat(condition.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.CLOTURE)).isFalse();
            assertThat(condition.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.EN_COURS)).isFalse();
            assertThat(condition.concerne(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE)).isFalse();
        }
    }

    // ------------------------------------------------------------------ Une audience tenue

    @Test
    void sans_audience_tenue_la_mise_en_delibere_est_refusee() {
        when(audienceRepository.existsByEtapeIdAndStatut(7L, StatutAudience.TENUE)).thenReturn(false);

        assertThat(audienceTenue.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .contains("Aucune audience tenue")
                        .contains("compte rendu"));
    }

    /**
     * « Tenue » veut dire « rapportée » : le statut n'est posé qu'à la saisie du compte rendu. C'est
     * l'arbitrage du métier, et c'est ce que cette condition vérifie — pas une date passée.
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

        // Une audience annulée ou simplement planifiée ne vaut pas audience tenue : la condition
        // n'interroge que TENUE, jamais « une audience existe ».
        org.mockito.Mockito.verify(audienceRepository).existsByEtapeIdAndStatut(7L, StatutAudience.TENUE);
        org.mockito.Mockito.verifyNoMoreInteractions(audienceRepository);
    }

    // ------------------------------------------------------------------ Aucune audience en attente

    @Test
    void une_audience_encore_planifiee_bloque_la_mise_en_delibere() {
        when(audienceRepository.countByEtapeIdAndStatut(7L, StatutAudience.PLANIFIEE)).thenReturn(1L);

        assertThat(aucuneEnAttente.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .startsWith("Une audience reste planifiée")
                        .contains("tenez-la ou annulez-la"));
    }

    /** Le nombre est dit : « des audiences » enverrait chercher combien, et lesquelles. */
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
     * Le cas que le métier visait : une audience s'est tenue, une autre reste au calendrier. La
     * première condition est remplie, la seconde non — et c'est bien la seconde qui doit parler.
     */
    @Test
    void une_audience_tenue_ne_rachete_pas_une_audience_encore_planifiee() {
        when(audienceRepository.existsByEtapeIdAndStatut(7L, StatutAudience.TENUE)).thenReturn(true);
        when(audienceRepository.countByEtapeIdAndStatut(7L, StatutAudience.PLANIFIEE)).thenReturn(1L);

        assertThat(audienceTenue.empechement(etape)).isEmpty();
        assertThat(aucuneEnAttente.empechement(etape)).isPresent();
    }
}
