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
 * On ne fait pas appel avant que le juge ait statué (2026-09-29).
 *
 * <p>Ces tests protègent aussi la <b>forme</b> de la règle. Le métier avait validé « l'étape
 * précédente doit être close » ; cette version-là aurait bloqué le parcours normal, l'exercice d'un
 * recours rouvrant l'étape courante en {@code EN_COURS} au lieu de la clore. Le test
 * {@code apres_un_recours_le_degre_superieur_s_ouvre} verrouille ce cas : s'il tombe un jour, c'est
 * que la règle a glissé vers la version impossible.
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
        // La reprise après recours part de DELIBERE_VIDE et porte sur l'étape elle-même.
        assertThat(condition.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.EN_COURS)).isFalse();
        assertThat(condition.concerne(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE)).isFalse();
        assertThat(condition.concerne(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE)).isFalse();
    }

    @Test
    void la_premiere_instance_n_a_aucun_antecedent() {
        EtapeProcedure instance = etape(1L, TypeEtape.INSTANCE, StatutCycleVie.OUVERTURE);

        assertThat(condition.empechement(instance)).isEmpty();
    }

    /** Le cas visé : l'instance attend son jugement, et l'on voudrait ouvrir les débats d'appel. */
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
     * Le parcours normal après un recours : l'instance est {@code EN_COURS}, pas close. Une règle
     * exigeant l'antécédent clos l'aurait bloqué — c'est pourquoi elle a été écartée.
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
     * Les deux cassations sont concurrentes : un pourvoi national en délibéré n'empêche pas
     * d'ouvrir les débats devant la CCJA. Un calcul par rang aurait fait l'inverse.
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
