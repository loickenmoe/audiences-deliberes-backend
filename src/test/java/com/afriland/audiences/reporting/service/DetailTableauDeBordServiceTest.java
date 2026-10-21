package com.afriland.audiences.reporting.service;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.decision.domain.Adjudication;
import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.reporting.domain.IndicateurTableauDeBord;
import com.afriland.audiences.reporting.web.dto.DetailIndicateurResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rows behind each dashboard block: which source each indicator reads, and how each kind of object
 * becomes a row. The queries themselves are checked against PostgreSQL in
 * {@link DetailTableauDeBordIntegrationTest}; here, the mapping, including objects with no case file.
 */
@ExtendWith(MockitoExtension.class)
class DetailTableauDeBordServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);
    private static final LocalDate JOUR = LocalDate.of(2026, 9, 15);

    @Mock private EtapeProcedureRepository etapeProcedureRepository;
    @Mock private AudienceRepository audienceRepository;
    @Mock private DemandeFraisRepository demandeFraisRepository;
    @Mock private AlerteRepository alerteRepository;
    @Mock private DelibereRepository delibereRepository;
    @Mock private DecisionDefinitiveRepository decisionDefinitiveRepository;
    @Mock private DossierRepository dossierRepository;

    @InjectMocks private DetailTableauDeBordService service;

    private static Dossier dossier() {
        return Dossier.builder().id(7L).reference("DOS-2026-0007").nature("Saisie immobilière")
                .juridictionSaisie("TGI Douala").risqueEncouru(1_500_000d)
                .client(Client.builder().id(3L).nom("Société Alpha").build()).build();
    }

    private static <T> Page<T> page(List<T> contenu) {
        return new PageImpl<>(contenu, PAGE, contenu.size());
    }

    @Test
    void dossiersActifs_etAffairesVidees_listentLesDossiers() {
        Dossier sansClient = Dossier.builder().id(8L).reference("DOS-2026-0008").nature("Recouvrement").build();
        when(etapeProcedureRepository.listerDossiersAvecEtapeEnCours(any(), anyList(), eq(PAGE)))
                .thenReturn(page(List.of(dossier(), sansClient)));
        when(etapeProcedureRepository.listerDossiersSansEtapeEnCours(any(), anyList(), eq(PAGE)))
                .thenReturn(page(List.of(dossier())));

        List<DetailIndicateurResponse> actifs =
                service.detailler(IndicateurTableauDeBord.DOSSIERS_ACTIFS, null, PAGE).getContent();
        List<DetailIndicateurResponse> videes =
                service.detailler(IndicateurTableauDeBord.AFFAIRES_VIDEES, CategorieDossier.RECOUVREMENT, PAGE).getContent();

        assertThat(actifs).containsExactly(
                new DetailIndicateurResponse(7L, 7L, "DOS-2026-0007", "Société Alpha", "Saisie immobilière", null, 1_500_000d),
                new DetailIndicateurResponse(8L, 8L, "DOS-2026-0008", null, "Recouvrement", null, 0d));
        assertThat(videes).hasSize(1);
        verify(etapeProcedureRepository).listerDossiersAvecEtapeEnCours(
                DetailTableauDeBordService.ETAPES_FERMEES, ReportingService.perimetre(null), PAGE);
        verify(etapeProcedureRepository).listerDossiersSansEtapeEnCours(
                DetailTableauDeBordService.ETAPES_FERMEES, ReportingService.perimetre(CategorieDossier.RECOUVREMENT), PAGE);
    }

    @Test
    void audiencesPlanifiees_avecEtSansDossier() {
        Audience rattachee = Audience.builder().id(11L).dossier(dossier()).juridiction("Cour d'appel du Littoral")
                .datePlanifiee(JOUR).build();
        Audience orpheline = Audience.builder().id(12L).juridiction("TGI Yaoundé").datePlanifiee(JOUR).build();
        when(audienceRepository.listerParStatut(eq(StatutAudience.PLANIFIEE), anyList(), eq(PAGE)))
                .thenReturn(page(List.of(rattachee, orpheline)));

        assertThat(service.detailler(IndicateurTableauDeBord.AUDIENCES_PLANIFIEES, null, PAGE).getContent()).containsExactly(
                new DetailIndicateurResponse(11L, 7L, "DOS-2026-0007", "Cour d'appel du Littoral", "Société Alpha", JOUR, null),
                new DetailIndicateurResponse(12L, null, null, "TGI Yaoundé", null, JOUR, null));
    }

    @Test
    void fraisEnAttente_montrentFactureAvocatDateEtMontant() {
        DemandeFrais complete = DemandeFrais.builder().id(21L).dossier(dossier()).referenceFacture("FAC-001")
                .avocat(Avocat.builder().id(5L).nom("Me Fotso").build()).montant(250_000d)
                .createdAt(JOUR.atTime(10, 30)).build();
        DemandeFrais minimale = DemandeFrais.builder().id(22L).referenceFacture("FAC-002").montant(10d).build();
        when(demandeFraisRepository.listerNonSoldees(eq(DetailTableauDeBordService.FRAIS_SOLDES), anyList(), eq(PAGE)))
                .thenReturn(page(List.of(complete, minimale)));

        assertThat(service.detailler(IndicateurTableauDeBord.FRAIS_EN_ATTENTE, null, PAGE).getContent()).containsExactly(
                new DetailIndicateurResponse(21L, 7L, "DOS-2026-0007", "FAC-001", "Me Fotso", JOUR, 250_000d),
                new DetailIndicateurResponse(22L, null, null, "FAC-002", null, null, 10d));
    }

    @ParameterizedTest
    @CsvSource({"LITIGES_GAGNES, FAVORABLE", "LITIGES_PERDUS, DEFAVORABLE", "DECISIONS_MIXTES, MIXTE"})
    void deliberes_selonLeurResultat(IndicateurTableauDeBord indicateur, ResultatDelibere resultat) {
        Delibere avecDossier = Delibere.builder().id(31L).dossier(dossier()).dateDeliberee(JOUR).build();
        Delibere sansDossier = Delibere.builder().id(32L).dateDeliberee(JOUR).build();
        when(delibereRepository.listerParResultat(eq(resultat), anyList(), eq(PAGE)))
                .thenReturn(page(List.of(avecDossier, sansDossier)));

        assertThat(service.detailler(indicateur, null, PAGE).getContent()).containsExactly(
                new DetailIndicateurResponse(31L, 7L, "DOS-2026-0007", "Société Alpha", "TGI Douala", JOUR, null),
                new DetailIndicateurResponse(32L, null, null, null, null, JOUR, null));
    }

    @Test
    void provisionsReprises_montrentBeneficiaireEtReliquat() {
        Adjudication adjudication = Adjudication.builder().id(41L).dossier(dossier()).beneficiaire("Afriland First Bank")
                .dateDecision(JOUR).reliquat(125_000d).build();
        Adjudication sansDossier = Adjudication.builder().id(42L).beneficiaire("Tiers").dateDecision(JOUR).build();
        when(decisionDefinitiveRepository.listerProvisionsReprises(anyList(), eq(PAGE)))
                .thenReturn(page(List.of(adjudication, sansDossier)));

        assertThat(service.detailler(IndicateurTableauDeBord.PROVISIONS_REPRISES, null, PAGE).getContent()).containsExactly(
                new DetailIndicateurResponse(41L, 7L, "DOS-2026-0007", "Afriland First Bank", "Société Alpha", JOUR, 125_000d),
                new DetailIndicateurResponse(42L, null, null, "Tiers", null, JOUR, 0d));
    }

    @Test
    void alertesDeProrogation_chargentLesReferencesEnUneFois() {
        Utilisateur dj = Utilisateur.builder().id(2L).nom("Directrice").build();
        Alerte premiere = Alerte.builder().id(51L).dossierId(7L).declencheur("Délibéré prorogé 3 fois").destinataire(dj).build();
        Alerte memeDossier = Alerte.builder().id(52L).dossierId(7L).declencheur("Délibéré prorogé 4 fois").destinataire(dj).build();
        Alerte horsDossier = Alerte.builder().id(53L).declencheur("Alerte système").build();
        when(alerteRepository.listerParTypeEtStatut(eq(TypeAlerte.PROROGATION), eq(StatutAlerte.DECLENCHEE), anyList(), eq(PAGE)))
                .thenReturn(page(List.of(premiere, memeDossier, horsDossier)));
        when(dossierRepository.findAllById(List.of(7L))).thenReturn(List.of(dossier()));

        assertThat(service.detailler(IndicateurTableauDeBord.PROROGATIONS_ALERTE, null, PAGE).getContent()).containsExactly(
                new DetailIndicateurResponse(51L, 7L, "DOS-2026-0007", "Délibéré prorogé 3 fois", "Directrice", null, null),
                new DetailIndicateurResponse(52L, 7L, "DOS-2026-0007", "Délibéré prorogé 4 fois", "Directrice", null, null),
                new DetailIndicateurResponse(53L, null, null, "Alerte système", null, null, null));
    }

    @Test
    void alertesSansDossier_neChargentAucunDossier() {
        Alerte horsDossier = Alerte.builder().id(54L).declencheur("Alerte système").build();
        when(alerteRepository.listerParTypeEtStatut(any(), any(), anyList(), eq(PAGE))).thenReturn(page(List.of(horsDossier)));

        assertThat(service.detailler(IndicateurTableauDeBord.PROROGATIONS_ALERTE, null, PAGE).getContent()).hasSize(1);
        verify(dossierRepository, never()).findAllById(any());
    }

    @Test
    void etapesFermeesEtFraisSoldes_sontCeuxDuCompteur() {
        assertThat(DetailTableauDeBordService.ETAPES_FERMEES).containsExactly(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE);
        assertThat(DetailTableauDeBordService.FRAIS_SOLDES).containsExactly(StatutCircuitFrais.PAYEE, StatutCircuitFrais.REJETEE);
    }
}
