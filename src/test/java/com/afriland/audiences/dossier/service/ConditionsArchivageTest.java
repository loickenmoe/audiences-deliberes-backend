package com.afriland.audiences.dossier.service;

import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.decision.service.DecisionRequiseAvantArchivage;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.frais.service.FraisSoldesAvantArchivage;
import com.afriland.audiences.referentiel.domain.Avocat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les deux conditions d'archivage (métier, 2026-09-29) : l'issue définitive enregistrée, les frais
 * soldés.
 *
 * <p>Elles vivent dans deux modules différents — {@code decision} et {@code frais} — mais portent
 * sur le même passage, et c'est ensemble qu'elles doivent parler. Elles sont donc éprouvées ici,
 * côté {@code dossier}, là où elles se rejoignent.
 */
@ExtendWith(MockitoExtension.class)
class ConditionsArchivageTest {

    @Mock private DecisionDefinitiveRepository decisionDefinitiveRepository;
    @Mock private DemandeFraisRepository demandeFraisRepository;
    @Mock private EtapeProcedureRepository etapeProcedureRepository;

    private DecisionRequiseAvantArchivage decisionRequise;
    private FraisSoldesAvantArchivage fraisSoldes;
    private Dossier dossier;
    private EtapeProcedure etape;

    @BeforeEach
    void setUp() {
        decisionRequise = new DecisionRequiseAvantArchivage(decisionDefinitiveRepository);
        fraisSoldes = new FraisSoldesAvantArchivage(demandeFraisRepository, etapeProcedureRepository);
        dossier = Dossier.builder().id(4L).build();
        etape = etape(21L, TypeEtape.INSTANCE, StatutCycleVie.CLOTURE);
    }

    private EtapeProcedure etape(long id, TypeEtape type, StatutCycleVie statut) {
        return EtapeProcedure.builder().id(id).type(type).statutCycleVie(statut).dossier(dossier).build();
    }

    /** Les étapes que le dossier porte, celle qu'on archive comprise. */
    private void dossierPorte(EtapeProcedure... etapes) {
        lenient().when(etapeProcedureRepository.findByDossierIdOrderByTypeAsc(4L)).thenReturn(List.of(etapes));
    }

    private void demandesEnCours(DemandeFrais... demandes) {
        lenient().when(demandeFraisRepository.findByDossierIdAndStatutCircuitNotIn(anyLong(), anyList()))
                .thenReturn(List.of(demandes));
    }

    private DemandeFrais demande(String facture, String avocat) {
        return DemandeFrais.builder()
                .referenceFacture(facture)
                .avocat(Avocat.builder().nom(avocat).build())
                .build();
    }

    private List<String> empechements() {
        return java.util.stream.Stream.of(decisionRequise, fraisSoldes)
                .filter(condition -> condition.concerne(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE))
                .map(condition -> condition.empechement(etape))
                .flatMap(Optional::stream)
                .toList();
    }

    @Test
    void les_deux_conditions_ne_valent_que_pour_l_archivage() {
        for (var condition : List.of(decisionRequise, fraisSoldes)) {
            assertThat(condition.concerne(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE)).isTrue();
            assertThat(condition.concerne(StatutCycleVie.DELIBERE_VIDE, StatutCycleVie.CLOTURE)).isFalse();
            assertThat(condition.concerne(StatutCycleVie.EN_COURS, StatutCycleVie.EN_DELIBERE)).isFalse();
        }
    }

    // ------------------------------------------------------------------ L'issue définitive

    @Test
    void sans_decision_definitive_l_archivage_est_refuse() {
        when(decisionDefinitiveRepository.existsByEtapeId(21L)).thenReturn(false);

        assertThat(decisionRequise.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .contains("Aucune décision définitive")
                        .contains("avant l'archivage"));
    }

    @Test
    void une_decision_enregistree_laisse_archiver() {
        when(decisionDefinitiveRepository.existsByEtapeId(21L)).thenReturn(true);

        assertThat(decisionRequise.empechement(etape)).isEmpty();
    }

    // ------------------------------------------------------------------ Les frais, et la dernière étape

    /**
     * Le cas soulevé par le métier : un dossier porte plusieurs avocats, donc un flux continu de
     * demandes. Tant qu'une autre étape vit, la première instance se classe — la facture en cours
     * appartient vraisemblablement à l'appel, et celui qui archive n'a pas la main dessus.
     */
    @Test
    void une_etape_ancienne_se_classe_meme_avec_des_frais_en_cours_ailleurs() {
        dossierPorte(etape, etape(22L, TypeEtape.RECOURS_1, StatutCycleVie.EN_COURS));
        demandesEnCours(demande("FAC-001", "Me Ndjock"));

        assertThat(fraisSoldes.empechement(etape)).isEmpty();
        // La question des frais n'est même pas posée : inutile d'interroger ce qu'on n'opposera pas.
        verify(demandeFraisRepository, never()).findByDossierIdAndStatutCircuitNotIn(anyLong(), anyList());
    }

    @Test
    void la_derniere_etape_ne_s_archive_pas_sur_des_frais_en_cours() {
        dossierPorte(etape, etape(22L, TypeEtape.RECOURS_1, StatutCycleVie.ARCHIVE));
        demandesEnCours(demande("FAC-001", "Me Ndjock"));

        assertThat(fraisSoldes.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .startsWith("Une demande de frais")
                        .contains("soldez-la")
                        .contains("dernière étape"));
    }

    @Test
    void une_etape_seule_est_la_derniere() {
        dossierPorte(etape);
        demandesEnCours(demande("FAC-009", "Me Kamga"));

        assertThat(fraisSoldes.empechement(etape)).isPresent();
    }

    /** Le refus nomme les demandes : celui qui archive doit savoir auprès de qui aller. */
    @Test
    void le_refus_nomme_les_demandes_et_leurs_avocats() {
        dossierPorte(etape);
        demandesEnCours(demande("FAC-001", "Me Ndjock"), demande("FAC-002", "Me Kamga"));

        assertThat(fraisSoldes.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .startsWith("2 demandes de frais")
                        .contains("FAC-001 – Me Ndjock")
                        .contains("FAC-002 – Me Kamga")
                        .contains("soldez-les"));
    }

    /** Sans référence de facture, l'avocat suffit à retrouver la demande. */
    @Test
    void une_demande_sans_facture_est_designee_par_son_avocat() {
        dossierPorte(etape);
        demandesEnCours(demande(null, "Me Ndjock"));

        assertThat(fraisSoldes.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif).contains("(Me Ndjock)"));
    }

    /** Au-delà de trois, la liste cesse d'aider : le reste est compté. */
    @Test
    void au_dela_de_trois_demandes_le_reste_est_compte() {
        dossierPorte(etape);
        demandesEnCours(demande("FAC-1", "A"), demande("FAC-2", "B"),
                demande("FAC-3", "C"), demande("FAC-4", "D"), demande("FAC-5", "E"));

        assertThat(fraisSoldes.empechement(etape))
                .hasValueSatisfying(motif -> assertThat(motif)
                        .contains("FAC-3 – C")
                        .doesNotContain("FAC-4")
                        .contains("et 2 autre(s)"));
    }

    /** Soldée veut dire payée **ou** rejetée : ce sont les deux fins du circuit. */
    @Test
    void seules_les_demandes_payees_ou_rejetees_sont_soldees() {
        dossierPorte(etape);
        demandesEnCours();

        fraisSoldes.empechement(etape);

        verify(demandeFraisRepository).findByDossierIdAndStatutCircuitNotIn(
                4L, List.of(StatutCircuitFrais.PAYEE, StatutCircuitFrais.REJETEE));
    }

    @Test
    void aucune_demande_en_cours_laisse_archiver() {
        dossierPorte(etape);
        demandesEnCours();

        assertThat(fraisSoldes.empechement(etape)).isEmpty();
    }

    // ------------------------------------------------------------------ Ensemble

    @Test
    void les_deux_manques_se_disent_ensemble() {
        dossierPorte(etape);
        demandesEnCours(demande("FAC-001", "Me Ndjock"));
        when(decisionDefinitiveRepository.existsByEtapeId(21L)).thenReturn(false);

        assertThat(empechements()).hasSize(2);
    }

    @Test
    void tout_en_ordre_n_empeche_rien() {
        dossierPorte(etape);
        demandesEnCours();
        when(decisionDefinitiveRepository.existsByEtapeId(21L)).thenReturn(true);

        assertThat(empechements()).isEmpty();
    }
}
