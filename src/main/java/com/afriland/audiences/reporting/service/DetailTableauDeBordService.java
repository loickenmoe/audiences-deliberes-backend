package com.afriland.audiences.reporting.service;

import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.reporting.domain.IndicateurTableauDeBord;
import com.afriland.audiences.reporting.web.dto.DetailIndicateurResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Le détail d'un bloc du tableau de bord (Q-137).
 *
 * <p><b>Ce que le métier a demandé</b> le 2026-10-13 : qu'un clic sur un bloc du tableau de bord
 * ouvre le détail de ce qu'il compte. C'est le même geste que sur la fiche d'activité (Q-133), mais
 * la mécanique n'a rien de commun : là, tout venait de {@code historique_action} ; ici, chaque
 * indicateur a sa propre source - des étapes, des audiences, des demandes de frais, des délibérés,
 * des alertes, des adjudications.
 *
 * <p><b>La seule propriété qui compte vraiment : le détail rend exactement ce que le compteur
 * compte.</b> Chaque liste reprend le prédicat de son compteur, mot pour mot, et un test
 * d'intégration confronte les deux sur la base réelle. Afficher « 12 » puis lister neuf lignes
 * ferait douter des deux chiffres, et c'est le genre de doute qu'on ne rattrape pas.
 *
 * <p><b>Les habilitations sont celles du tableau de bord</b>, {@code hasAnyRole('DJ','DJA')}, et
 * c'est le contrôleur qui les porte : ce service ne sert qu'à ouvrir ce que l'écran affiche déjà.
 */
@Service
@RequiredArgsConstructor
public class DetailTableauDeBordService {

    /**
     * Les mêmes constantes que {@link ReportingService}, et non une copie d'intention.
     *
     * <p>Elles y sont privées ; les redéclarer est le prix à payer pour ne pas élargir leur
     * visibilité, mais c'est aussi un risque de dérive. Un test les confronte terme à terme, parce
     * qu'une divergence silencieuse ferait rendre au détail autre chose que ce qui est compté.
     */
    static final List<StatutCycleVie> ETAPES_FERMEES = List.of(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE);

    static final List<StatutCircuitFrais> FRAIS_SOLDES =
            List.of(StatutCircuitFrais.PAYEE, StatutCircuitFrais.REJETEE);

    private final EtapeProcedureRepository etapeProcedureRepository;
    private final AudienceRepository audienceRepository;
    private final DemandeFraisRepository demandeFraisRepository;
    private final AlerteRepository alerteRepository;
    private final DelibereRepository delibereRepository;
    private final DecisionDefinitiveRepository decisionDefinitiveRepository;
    private final DossierRepository dossierRepository;

    @Transactional(readOnly = true)
    public Page<DetailIndicateurResponse> detailler(IndicateurTableauDeBord indicateur, Pageable pageable) {
        return switch (indicateur) {
            case DOSSIERS_ACTIFS -> etapeProcedureRepository
                    .listerDossiersAvecEtapeEnCours(ETAPES_FERMEES, pageable)
                    .map(DetailTableauDeBordService::versLigneDossier);
            case AFFAIRES_VIDEES -> etapeProcedureRepository
                    .listerDossiersSansEtapeEnCours(ETAPES_FERMEES, pageable)
                    .map(DetailTableauDeBordService::versLigneDossier);
            case AUDIENCES_PLANIFIEES -> audienceRepository
                    .findByStatutOrderByDatePlanifieeAsc(StatutAudience.PLANIFIEE, pageable)
                    .map(audience -> new DetailIndicateurResponse(
                            audience.getId(),
                            audience.getDossier() == null ? null : audience.getDossier().getId(),
                            audience.getDossier() == null ? null : audience.getDossier().getReference(),
                            audience.getJuridiction(),
                            nomDuClient(audience.getDossier()),
                            audience.getDatePlanifiee(),
                            null));
            case FRAIS_EN_ATTENTE -> demandeFraisRepository
                    .findByStatutCircuitNotInOrderByCreatedAtAsc(FRAIS_SOLDES, pageable)
                    .map(demande -> new DetailIndicateurResponse(
                            demande.getId(),
                            demande.getDossier() == null ? null : demande.getDossier().getId(),
                            demande.getDossier() == null ? null : demande.getDossier().getReference(),
                            demande.getReferenceFacture(),
                            demande.getAvocat() == null ? null : demande.getAvocat().getNom(),
                            demande.getCreatedAt() == null ? null : demande.getCreatedAt().toLocalDate(),
                            demande.getMontant()));
            case PROROGATIONS_ALERTE -> versLignesAlertes(pageable);
            case LITIGES_GAGNES -> versLignesDeliberes(ResultatDelibere.FAVORABLE, pageable);
            case LITIGES_PERDUS -> versLignesDeliberes(ResultatDelibere.DEFAVORABLE, pageable);
            case DECISIONS_MIXTES -> versLignesDeliberes(ResultatDelibere.MIXTE, pageable);
            case PROVISIONS_REPRISES -> decisionDefinitiveRepository.listerProvisionsReprises(pageable)
                    .map(adjudication -> new DetailIndicateurResponse(
                            adjudication.getId(),
                            adjudication.getDossier() == null ? null : adjudication.getDossier().getId(),
                            adjudication.getDossier() == null ? null : adjudication.getDossier().getReference(),
                            adjudication.getBeneficiaire(),
                            nomDuClient(adjudication.getDossier()),
                            adjudication.getDateDecision(),
                            adjudication.getReliquat()));
        };
    }

    private Page<DetailIndicateurResponse> versLignesDeliberes(ResultatDelibere resultat, Pageable pageable) {
        return delibereRepository.listerParResultatEnRecouvrement(resultat, pageable)
                .map(delibere -> new DetailIndicateurResponse(
                        delibere.getId(),
                        delibere.getDossier() == null ? null : delibere.getDossier().getId(),
                        delibere.getDossier() == null ? null : delibere.getDossier().getReference(),
                        nomDuClient(delibere.getDossier()),
                        delibere.getDossier() == null ? null : delibere.getDossier().getJuridictionSaisie(),
                        delibere.getDateDeliberee(),
                        null));
    }

    /**
     * Les alertes de prorogation en cours.
     *
     * <p>Une alerte porte un {@code dossierId} libre, sans relation : la référence du dossier se lit
     * donc en une seconde requête, groupée pour la page entière. Une lecture par ligne ferait dix
     * requêtes pour une page qui pointe souvent vers le même dossier.
     */
    private Page<DetailIndicateurResponse> versLignesAlertes(Pageable pageable) {
        var alertes = alerteRepository.findByTypeAndStatutOrderByIdDesc(
                TypeAlerte.PROROGATION, StatutAlerte.DECLENCHEE, pageable);
        List<Long> dossiers = alertes.getContent().stream()
                .map(alerte -> alerte.getDossierId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> references = dossiers.isEmpty()
                ? Map.of()
                : dossierRepository.findAllById(dossiers).stream()
                        .collect(java.util.stream.Collectors.toMap(Dossier::getId, Dossier::getReference));
        return alertes.map(alerte -> new DetailIndicateurResponse(
                alerte.getId(),
                alerte.getDossierId(),
                alerte.getDossierId() == null ? null : references.get(alerte.getDossierId()),
                alerte.getDeclencheur(),
                alerte.getDestinataire() == null ? null : alerte.getDestinataire().getNom(),
                null,
                null));
    }

    private static DetailIndicateurResponse versLigneDossier(Dossier dossier) {
        return new DetailIndicateurResponse(
                dossier.getId(),
                dossier.getId(),
                dossier.getReference(),
                nomDuClient(dossier),
                dossier.getNature(),
                null,
                dossier.getRisqueEncouru());
    }

    /** Le client nomme le dossier mieux que sa référence : c'est de lui qu'on se souvient. */
    private static String nomDuClient(Dossier dossier) {
        return dossier == null || dossier.getClient() == null ? null : dossier.getClient().getNom();
    }
}
