package com.afriland.audiences.reporting.service;

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
 * Épic 8, volet reporting (FR-REP-01/02, RG-REP-01/02/03, US 8.4/8.5) — cf. FUNCTIONAL_MAP.md §8,
 * API_IMPLEMENTATION_STATUS.md §9. US 8.6 (vue consolidée par client) est déjà couverte depuis M4
 * par {@code DossierService.vueConsolideeParClient} — non dupliquée ici.
 */
@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final List<StatutCycleVie> ETAPES_FERMEES = List.of(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE);
    private static final List<StatutCircuitFrais> FRAIS_SOLDES = List.of(StatutCircuitFrais.PAYEE, StatutCircuitFrais.REJETEE);

    private final EtapeProcedureRepository etapeProcedureRepository;
    private final AudienceRepository audienceRepository;
    private final DemandeFraisRepository demandeFraisRepository;
    private final AlerteRepository alerteRepository;
    private final DelibereRepository delibereRepository;
    private final DecisionDefinitiveRepository decisionDefinitiveRepository;
    private final DossierRepository dossierRepository;

    /** FR-REP-02/RG-REP-03 : indicateurs temps réel, tous dossiers confondus. */
    @Transactional(readOnly = true)
    public TableauDeBordResponse tableauDeBord() {
        long nbDossiersActifs = etapeProcedureRepository.countDistinctDossierIdByStatutCycleVieNotIn(ETAPES_FERMEES);
        long nbAudiencesPlanifiees = audienceRepository.countByStatut(StatutAudience.PLANIFIEE);
        long nbDemandesFraisEnAttente = demandeFraisRepository.countByStatutCircuitNotIn(FRAIS_SOLDES);
        long nbProrogationsAlerte = alerteRepository.countByTypeAndStatut(TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE);
        DelibereRepository.PerformanceGlobale performance = delibereRepository.calculerTauxSuccesRecouvrement();
        double tauxSuccesRecouvrement = tauxSucces(performance.getFavorables(), performance.getTotal());
        double provisionsReprises = valeurOuZero(decisionDefinitiveRepository.sommeProvisionsReprises());

        return new TableauDeBordResponse(nbDossiersActifs, nbAudiencesPlanifiees, nbDemandesFraisEnAttente,
                nbProrogationsAlerte, tauxSuccesRecouvrement,
                nombreOuZero(performance.getFavorables()), nombreOuZero(performance.getDefavorables()),
                nombreOuZero(performance.getMixtes()), provisionsReprises);
    }

    /**
     * FR-REP-01/RG-REP-01/02 : rapport restreint à {@code [dateDebut, dateFin]}. {@code type} est
     * une étiquette de période sans effet de calcul, {@code dateDebut}/{@code dateFin} étant
     * explicitement fournies par l'appelant (Q-63).
     */
    @Transactional(readOnly = true)
    public RapportResponse genererRapport(TypeRapport type, LocalDate dateDebut, LocalDate dateFin) {
        LocalDateTime debutJour = dateDebut.atStartOfDay();
        LocalDateTime finJour = dateFin.atTime(23, 59, 59);

        long nbNouveauxDossiers = dossierRepository.countByCreatedAtBetween(debutJour, finJour);
        long nbAudiencesTenues = audienceRepository.countByStatutAndDateTenueBetween(StatutAudience.TENUE, dateDebut, dateFin);
        long nbDemandesFraisDeposees = demandeFraisRepository.countByCreatedAtBetween(debutJour, finJour);
        // Délibérés rendus dans la période : une mise en délibéré sans résultat n'en est pas un (Q-78).
        long nbDeliberesEnregistres = delibereRepository.countByDateDelibereeBetweenAndResultatIsNotNull(dateDebut, dateFin);
        DelibereRepository.PerformanceGlobale performance = delibereRepository.calculerTauxSuccesRecouvrementPeriode(dateDebut, dateFin);
        double tauxSuccesRecouvrement = tauxSucces(performance.getFavorables(), performance.getTotal());
        double provisionsReprises = valeurOuZero(decisionDefinitiveRepository.sommeProvisionsReprisesPeriode(dateDebut, dateFin));

        return new RapportResponse(type, dateDebut, dateFin, nbNouveauxDossiers, nbAudiencesTenues,
                nbDemandesFraisDeposees, nbDeliberesEnregistres, tauxSuccesRecouvrement, provisionsReprises);
    }

    private double tauxSucces(Long favorables, Long total) {
        if (total == null || total == 0) {
            return 0d;
        }
        return (double) (favorables == null ? 0L : favorables) / total;
    }

    /**
     * {@code SUM} rend {@code null} sur un ensemble vide, non zéro : une base sans délibéré rendu
     * ferait échouer l'autoboxing vers {@code long}.
     */
    private long nombreOuZero(Long valeur) {
        return valeur == null ? 0L : valeur;
    }

    private double valeurOuZero(Double valeur) {
        return valeur == null ? 0d : valeur;
    }
}
