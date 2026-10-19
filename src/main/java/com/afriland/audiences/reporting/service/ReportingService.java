package com.afriland.audiences.reporting.service;

import com.afriland.audiences.referentiel.domain.CategorieDossier;

import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.reporting.domain.TypeRapport;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import com.afriland.audiences.reporting.web.dto.TableauDeBordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reporting (FR-REP-01/02, RG-REP-01/02/03, US 8.4/8.5). US 8.6 (consolidated view per client) is
 * covered by {@code DossierService.vueConsolideeParClient}.
 */
@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final List<StatutCycleVie> ETAPES_FERMEES = List.of(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE);

    /**
     * Default scope: both categories. {@code categorie} is {@code NOT NULL} with a {@code CHECK} on
     * exactly these values, so "all categories" is their union. An explicit list avoids an
     * optional {@code :categorie IS NULL OR ...} filter in every query and any unscoped call.
     */
    static final List<CategorieDossier> TOUTES_CATEGORIES = List.of(CategorieDossier.values());

    /** The requested scope, or both categories by default. */
    static List<CategorieDossier> perimetre(CategorieDossier categorie) {
        return categorie == null ? TOUTES_CATEGORIES : List.of(categorie);
    }
    private static final List<StatutCircuitFrais> FRAIS_SOLDES = List.of(StatutCircuitFrais.PAYEE, StatutCircuitFrais.REJETEE);

    private final EtapeProcedureRepository etapeProcedureRepository;
    private final AudienceRepository audienceRepository;
    private final DemandeFraisRepository demandeFraisRepository;
    private final AlerteRepository alerteRepository;
    private final DelibereRepository delibereRepository;
    private final DecisionDefinitiveRepository decisionDefinitiveRepository;
    private final DossierRepository dossierRepository;

    /** Real-time indicators across all cases (FR-REP-02, RG-REP-03). */
    @Transactional(readOnly = true)
    public TableauDeBordResponse tableauDeBord(CategorieDossier categorie) {
        // All indicators share the same category scope.
        List<CategorieDossier> categories = perimetre(categorie);
        long nbDossiersActifs = etapeProcedureRepository
                .countDistinctDossierIdByStatutCycleVieNotIn(ETAPES_FERMEES, categories);
        // Closed matters: no step still in progress, the exact complement of the counter above.
        long nbAffairesVidees = etapeProcedureRepository.countDossiersSansEtapeEnCours(ETAPES_FERMEES, categories);
        long nbAudiencesPlanifiees = audienceRepository.compterParStatut(StatutAudience.PLANIFIEE, categories);
        long nbDemandesFraisEnAttente = demandeFraisRepository.compterNonSoldees(FRAIS_SOLDES, categories);
        long nbProrogationsAlerte = alerteRepository.compterParTypeEtStatut(
                TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, categories);
        DelibereRepository.PerformanceGlobale performance = delibereRepository.calculerResultats(categories);
        double tauxSuccesRecouvrement = tauxSucces(performance.getFavorables(), performance.getTotal());
        double provisionsReprises = valeurOuZero(decisionDefinitiveRepository.sommeProvisionsReprises(categories));

        return new TableauDeBordResponse(nbDossiersActifs, nbAudiencesPlanifiees, nbDemandesFraisEnAttente,
                nbProrogationsAlerte, tauxSuccesRecouvrement,
                nombreOuZero(performance.getFavorables()), nombreOuZero(performance.getDefavorables()),
                nombreOuZero(performance.getMixtes()), nbAffairesVidees, provisionsReprises);
    }

    /**
     * Report restricted to {@code [dateDebut, dateFin]} (FR-REP-01, RG-REP-01/02). {@code type} is
     * only a period label; the caller supplies the dates explicitly.
     */
    @Transactional(readOnly = true)
    public RapportResponse genererRapport(TypeRapport type, LocalDate dateDebut, LocalDate dateFin,
                                          CategorieDossier categorie) {
        LocalDateTime debutJour = dateDebut.atStartOfDay();
        LocalDateTime finJour = dateFin.atTime(23, 59, 59);
        // Same category scope as the dashboard, applied to every indicator.
        List<CategorieDossier> categories = perimetre(categorie);

        long nbNouveauxDossiers = dossierRepository.compterCreesEntre(debutJour, finJour, categories);
        long nbAudiencesTenues = audienceRepository.compterTenuesEntre(
                StatutAudience.TENUE, dateDebut, dateFin, categories);
        long nbDemandesFraisDeposees = demandeFraisRepository.compterDeposeesEntre(debutJour, finJour, categories);
        // Deliberations rendered in the period: one without a result does not count.
        long nbDeliberesEnregistres = delibereRepository.compterRendusEntre(dateDebut, dateFin, categories);
        DelibereRepository.PerformanceGlobale performance =
                delibereRepository.calculerResultatsPeriode(dateDebut, dateFin, categories);
        // The rate follows the requested scope too, so despite its name it is not limited to debt
        // recovery. It is no longer displayed.
        double tauxSuccesRecouvrement = tauxSucces(performance.getFavorables(), performance.getTotal());
        double provisionsReprises = valeurOuZero(
                decisionDefinitiveRepository.sommeProvisionsReprisesPeriode(dateDebut, dateFin, categories));

        return new RapportResponse(type, dateDebut, dateFin, nbNouveauxDossiers, nbAudiencesTenues,
                nbDemandesFraisDeposees, nbDeliberesEnregistres, tauxSuccesRecouvrement,
                nombreOuZero(performance.getFavorables()), nombreOuZero(performance.getDefavorables()),
                nombreOuZero(performance.getMixtes()), provisionsReprises);
    }

    private double tauxSucces(Long favorables, Long total) {
        if (total == null || total == 0) {
            return 0d;
        }
        return (double) (favorables == null ? 0L : favorables) / total;
    }

    /** {@code SUM} returns {@code null} on an empty set, which would break unboxing to {@code long}. */
    private long nombreOuZero(Long valeur) {
        return valeur == null ? 0L : valeur;
    }

    private double valeurOuZero(Double valeur) {
        return valeur == null ? 0d : valeur;
    }
}
