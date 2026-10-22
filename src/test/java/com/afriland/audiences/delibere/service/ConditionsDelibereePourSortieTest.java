package com.afriland.audiences.delibere.service;

import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.delibere.service.ConditionsDelibereePourSortie.ClotureExigeUnResultat;
import com.afriland.audiences.delibere.service.ConditionsDelibereePourSortie.RecoursDansLeDelai;
import com.afriland.audiences.delibere.service.ConditionsDelibereePourSortie.RecoursReserveAuxJugementsDefavorables;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Exit conditions of a deliberation.
 *
 * <p>The central case is an <b>appeal against a won judgment</b>: since this transition
 * automatically opens the next step, a favourable judgment could otherwise start an appeal nobody
 * decided on.
 */
@ExtendWith(MockitoExtension.class)
class ConditionsDelibereePourSortieTest {

    @Mock private DelibereRepository delibereRepository;

    private RecoursReserveAuxJugementsDefavorables recoursDefavorable;
    private RecoursDansLeDelai recoursDansLeDelai;
    private ClotureExigeUnResultat clotureAvecResultat;
    private EtapeProcedure etape;

    @BeforeEach
    void setUp() {
        recoursDefavorable = new RecoursReserveAuxJugementsDefavorables(delibereRepository);
        recoursDansLeDelai = new RecoursDansLeDelai(delibereRepository);
        clotureAvecResultat = new ClotureExigeUnResultat(delibereRepository);
        etape = EtapeProcedure.builder().id(12L).build();
    }

    private void dernierDelibere(ResultatDelibere resultat, LocalDateTime echeance) {
        when(delibereRepository.findFirstByEtapeIdAndResultatIsNotNullOrderByIdDesc(12L))
                .thenReturn(Optional.of(Delibere.builder()
                        .id(3L).resultat(resultat).dateEcheanceRecours(echeance).build()));
    }

    private void aucunDelibereRendu() {
        when(delibereRepository.findFirstByEtapeIdAndResultatIsNotNullOrderByIdDesc(12L))
                .thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------ Scope

    @Test
    void les_conditions_de_recours_ne_valent_que_pour_la_reouverture() {
        for (var condition : java.util.List.of(recoursDefavorable, recoursDansLeDelai)) {
            assertThat(condition.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.EN_COURS)).isTrue();
            assertThat(condition.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.CLOTURE)).isFalse();
            assertThat(condition.concerne(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE)).isFalse();
        }
        assertThat(clotureAvecResultat.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.CLOTURE)).isTrue();
        assertThat(clotureAvecResultat.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.EN_COURS)).isFalse();
    }

    // ------------------------------------------------------------------ Appeal

    /** The defect the rule targets: no appeal against a won case. */
    @Test
    void un_jugement_favorable_interdit_le_recours() {
        dernierDelibere(ResultatDelibere.FAVORABLE, null);

        assertThat(recoursDefavorable.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .contains("favorable")
                        .contains("défavorable ou mixte"));
    }

    @Test
    void un_jugement_defavorable_ouvre_le_recours() {
        dernierDelibere(ResultatDelibere.DEFAVORABLE, LocalDateTime.now().plusDays(10));

        assertThat(recoursDefavorable.empechement(etape)).isEmpty();
    }

    @Test
    void un_jugement_mixte_ouvre_aussi_le_recours() {
        dernierDelibere(ResultatDelibere.MIXTE, LocalDateTime.now().plusDays(10));

        assertThat(recoursDefavorable.empechement(etape)).isEmpty();
    }

    @Test
    void sans_delibere_rendu_il_n_y_a_rien_contre_quoi_recourir() {
        aucunDelibereRendu();

        assertThat(recoursDefavorable.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif).contains("Aucun délibéré rendu"));
    }

    // ------------------------------------------------------------------ Deadline

    @Test
    void un_delai_expire_interdit_de_rouvrir_l_etape() {
        LocalDateTime hier = LocalDateTime.now().minusDays(1);
        dernierDelibere(ResultatDelibere.DEFAVORABLE, hier);

        assertThat(recoursDansLeDelai.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .contains("délai de recours a expiré")
                        .contains(hier.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
    }

    @Test
    void un_delai_courant_laisse_passer() {
        dernierDelibere(ResultatDelibere.DEFAVORABLE, LocalDateTime.now().plusDays(3));

        assertThat(recoursDansLeDelai.empechement(etape)).isEmpty();
    }

    /**
     * A favourable judgment has no deadline ({@code validerEcheanceRecours} forbids it). This condition
     * stays silent and the other one refuses: two rules, two reasons, never a message about a
     * missing date.
     */
    @Test
    void sans_echeance_cette_condition_se_tait() {
        dernierDelibere(ResultatDelibere.FAVORABLE, null);

        assertThat(recoursDansLeDelai.empechement(etape)).isEmpty();
    }

    // ------------------------------------------------------------------ Closure

    @Test
    void la_cloture_exige_que_le_delibere_soit_rendu() {
        aucunDelibereRendu();

        assertThat(clotureAvecResultat.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif).contains("avant la clôture"));
    }

    @Test
    void un_delibere_rendu_autorise_la_cloture_quel_que_soit_son_sens() {
        dernierDelibere(ResultatDelibere.FAVORABLE, null);

        assertThat(clotureAvecResultat.empechement(etape)).isEmpty();
    }
}
