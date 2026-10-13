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
 * Épic 8, volet reporting (FR-REP-01/02, RG-REP-01/02/03, US 8.4/8.5) — cf. FUNCTIONAL_MAP.md §8,
 * API_IMPLEMENTATION_STATUS.md §9. US 8.6 (vue consolidée par client) est déjà couverte depuis M4
 * par {@code DossierService.vueConsolideeParClient} — non dupliquée ici.
 */
@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final List<StatutCycleVie> ETAPES_FERMEES = List.of(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE);

    /**
     * Le périmètre par défaut : <b>les deux catégories</b> (Q-138).
     *
     * <p><b>Pourquoi une liste des deux valeurs et non un filtre optionnel.</b> {@code categorie}
     * est {@code NOT NULL} en base, avec une contrainte {@code CHECK} sur ces deux valeurs exactement
     * (V1) : « toutes catégories » est donc littéralement leur union, et l'invariant
     * <b>Recouvrement + Litiges d'exploitation = Toutes</b> repose sur la base, non sur ce code. Un
     * filtre optionnel aurait demandé un {@code :categorie IS NULL OR …} dans seize requêtes, et
     * laissé la porte ouverte à un appel non scopé.
     */
    static final List<CategorieDossier> TOUTES_CATEGORIES = List.of(CategorieDossier.values());

    /** Le périmètre demandé, ou les deux catégories à défaut. */
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

    /** FR-REP-02/RG-REP-03 : indicateurs temps réel, tous dossiers confondus. */
    @Transactional(readOnly = true)
    public TableauDeBordResponse tableauDeBord(CategorieDossier categorie) {
        /*
         * Les neuf indicateurs suivent le même périmètre, et c'est tout l'objet de Q-138 : six
         * d'entre eux portaient sur les deux catégories, trois sur le recouvrement seul, sans que
         * l'écran le distingue. Un dossier d'exploitation jugé ne faisait donc bouger aucun des
         * compteurs de résultat - son délibéré existait sans être compté nulle part.
         */
        List<CategorieDossier> categories = perimetre(categorie);
        long nbDossiersActifs = etapeProcedureRepository
                .countDistinctDossierIdByStatutCycleVieNotIn(ETAPES_FERMEES, categories);
        // Les affaires vidées : plus aucune étape en cours (métier, 2026-10-06). Le complément
        // exact du compteur ci-dessus, et la même notion qu'au Q-53.
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
     * FR-REP-01/RG-REP-01/02 : rapport restreint à {@code [dateDebut, dateFin]}. {@code type} est
     * une étiquette de période sans effet de calcul, {@code dateDebut}/{@code dateFin} étant
     * explicitement fournies par l'appelant (Q-63).
     */
    @Transactional(readOnly = true)
    public RapportResponse genererRapport(TypeRapport type, LocalDate dateDebut, LocalDate dateFin,
                                          CategorieDossier categorie) {
        LocalDateTime debutJour = dateDebut.atStartOfDay();
        LocalDateTime finJour = dateFin.atTime(23, 59, 59);
        /*
         * Le rapport suit le même périmètre que le tableau de bord (Q-138). Il portait la même
         * asymétrie : quatre indicateurs sur les deux catégories, trois sur le recouvrement seul.
         * Ne scoper que les trois aurait reproduit le défaut qu'on retire.
         */
        List<CategorieDossier> categories = perimetre(categorie);

        long nbNouveauxDossiers = dossierRepository.compterCreesEntre(debutJour, finJour, categories);
        long nbAudiencesTenues = audienceRepository.compterTenuesEntre(
                StatutAudience.TENUE, dateDebut, dateFin, categories);
        long nbDemandesFraisDeposees = demandeFraisRepository.compterDeposeesEntre(debutJour, finJour, categories);
        // Délibérés rendus dans la période : une mise en délibéré sans résultat n'en est pas un (Q-78).
        long nbDeliberesEnregistres = delibereRepository.compterRendusEntre(dateDebut, dateFin, categories);
        DelibereRepository.PerformanceGlobale performance =
                delibereRepository.calculerResultatsPeriode(dateDebut, dateFin, categories);
        /*
         * Le taux suit désormais le périmètre, et son nom est devenu historique : sous « toutes
         * catégories » il ne mesure plus le seul recouvrement. Aucun écran ne l'affiche depuis le
         * 2026-10-06 ; le laisser figé au recouvrement aurait fait, dans la même réponse, un champ
         * qui ignore le périmètre que tous les autres respectent.
         */
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
