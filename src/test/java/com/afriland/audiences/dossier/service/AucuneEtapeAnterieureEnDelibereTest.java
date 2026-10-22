package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * No appeal before the judge has ruled.
 *
 * <p>Also guards the <b>shape</b> of the rule: requiring the previous step to be closed would block the
 * normal flow, since exercising a remedy reopens the current step as {@code EN_COURS} instead of
 * closing it. {@code apres_un_recours_le_degre_superieur_s_ouvre} locks this case.
 */
@ExtendWith(MockitoExtension.class)
class AucuneEtapeAnterieureEnDelibereTest {

    @Mock private EtapeProcedureRepository etapeProcedureRepository;

    private AucuneEtapeAnterieureEnDelibere condition;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        condition = new AucuneEtapeAnterieureEnDelibere(etapeProcedureRepository);
        dossier = Dossier.builder().id(5L).build();
    }

    private EtapeProcedure etape(long id, TypeEtape type, StatutCycleVie statut) {
        return EtapeProcedure.builder().id(id).type(type).statutCycleVie(statut).dossier(dossier).build();
    }

    private void dossierPorte(EtapeProcedure... etapes) {
        lenient().when(etapeProcedureRepository.findByDossierIdOrderByTypeAsc(5L)).thenReturn(List.of(etapes));
    }

    @Test
    void la_condition_ne_vaut_que_pour_l_ouverture_des_debats() {
        assertThat(condition.concerne(StatutCycleVie.OUVERTURE, StatutCycleVie.EN_COURS)).isTrue();
        // Resumption after a remedy starts from DELIBERE_VIDE and applies to the step itself.
        assertThat(condition.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.EN_COURS)).isFalse();
        assertThat(condition.concerne(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE)).isFalse();
        assertThat(condition.concerne(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE)).isFalse();
    }

    @Test
    void la_premiere_instance_n_a_aucun_antecedent() {
        EtapeProcedure instance = etape(1L, TypeEtape.INSTANCE, StatutCycleVie.OUVERTURE);

        assertThat(condition.empechement(instance)).isEmpty();
    }

    /** The target case: the first instance awaits its judgment, and appeal hearings would be opened. */
    @Test
    void une_instance_en_delibere_empeche_d_ouvrir_l_appel() {
        EtapeProcedure appel = etape(2L, TypeEtape.RECOURS_1, StatutCycleVie.OUVERTURE);
        dossierPorte(etape(1L, TypeEtape.INSTANCE, StatutCycleVie.EN_DELIBERE), appel);

        assertThat(condition.empechement(appel))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .contains("Instance")
                        .contains("en délibéré")
                        .doesNotContain("INSTANCE", "EN_DELIBERE"));
    }

    /**
     * The normal flow after a remedy: the first instance is {@code EN_COURS}, not closed. A rule
     * requiring the previous step to be closed would have blocked it.
     */
    @Test
    void apres_un_recours_le_degre_superieur_s_ouvre() {
        EtapeProcedure appel = etape(2L, TypeEtape.RECOURS_1, StatutCycleVie.OUVERTURE);
        dossierPorte(etape(1L, TypeEtape.INSTANCE, StatutCycleVie.EN_COURS), appel);

        assertThat(condition.empechement(appel)).isEmpty();
    }

    @Test
    void une_instance_close_n_empeche_rien() {
        EtapeProcedure appel = etape(2L, TypeEtape.RECOURS_1, StatutCycleVie.OUVERTURE);
        dossierPorte(etape(1L, TypeEtape.INSTANCE, StatutCycleVie.CLOTURE), appel);

        assertThat(condition.empechement(appel)).isEmpty();
    }

    /**
     * The two cassation appeals run concurrently: a national appeal under deliberation does not prevent
     * opening hearings before the CCJA. A rank-based computation would have done the opposite.
     */
    @Test
    void un_pourvoi_national_en_delibere_n_empeche_pas_la_CCJA() {
        EtapeProcedure ccja = etape(4L, TypeEtape.RECOURS_2_CCJA, StatutCycleVie.OUVERTURE);
        dossierPorte(
                etape(1L, TypeEtape.INSTANCE, StatutCycleVie.CLOTURE),
                etape(2L, TypeEtape.RECOURS_1, StatutCycleVie.CLOTURE),
                etape(3L, TypeEtape.RECOURS_2, StatutCycleVie.EN_DELIBERE),
                ccja);

        assertThat(condition.empechement(ccja)).isEmpty();
    }

    @Test
    void un_appel_en_delibere_empeche_les_deux_cassations() {
        for (TypeEtape cassation : List.of(TypeEtape.RECOURS_2, TypeEtape.RECOURS_2_CCJA)) {
            EtapeProcedure pourvoi = etape(4L, cassation, StatutCycleVie.OUVERTURE);
            when(etapeProcedureRepository.findByDossierIdOrderByTypeAsc(5L)).thenReturn(List.of(
                    etape(1L, TypeEtape.INSTANCE, StatutCycleVie.CLOTURE),
                    etape(2L, TypeEtape.RECOURS_1, StatutCycleVie.EN_DELIBERE),
                    pourvoi));

            assertThat(condition.empechement(pourvoi))
                    .as("cassation %s", cassation)
                    .hasValueSatisfying(motif -> assertThat(motif).contains("Appel"));
        }
    }
}
