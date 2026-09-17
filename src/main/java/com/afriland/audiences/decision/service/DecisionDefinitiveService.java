package com.afriland.audiences.decision.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.decision.domain.Adjudication;
import com.afriland.audiences.decision.domain.CondamnationPecuniaire;
import com.afriland.audiences.decision.mapper.AdjudicationMapper;
import com.afriland.audiences.decision.mapper.CondamnationMapper;
import com.afriland.audiences.decision.repository.DecisionDefinitiveRepository;
import com.afriland.audiences.decision.web.dto.AdjudicationResponse;
import com.afriland.audiences.decision.web.dto.CondamnationResponse;
import com.afriland.audiences.decision.web.dto.CreerAdjudicationRequest;
import com.afriland.audiences.decision.web.dto.CreerCondamnationRequest;
import com.afriland.audiences.decision.web.dto.DecisionsDossierResponse;
import com.afriland.audiences.decision.web.dto.ModifierStatutCondamnationRequest;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Épic 8 partiel (RG-DEF-01 à 04) — cf. FUNCTIONAL_MAP.md §8, API_IMPLEMENTATION_STATUS.md §10.
 * Le volet jurisprudence (US 8.3) vit dans {@link JurisprudenceService}.
 */
@Service
@RequiredArgsConstructor
public class DecisionDefinitiveService {

    private final DecisionDefinitiveRepository decisionDefinitiveRepository;
    private final DossierRepository dossierRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;
    private final HistoriqueActionService historiqueActionService;
    private final AdjudicationMapper adjudicationMapper;
    private final CondamnationMapper condamnationMapper;

    /**
     * 🆕 M16 (Q-88, QF-03 du frontend) : décisions d'un dossier — aucune lecture n'existait, l'onglet
     * de la fiche restait en écriture seule et la mise à jour d'une condamnation (#53) inatteignable
     * après un rechargement. 404 si le dossier est inconnu, comme #56/#57/#58.
     */
    @Transactional(readOnly = true)
    public DecisionsDossierResponse lister(Long dossierId) {
        getDossierOrThrow(dossierId);
        return new DecisionsDossierResponse(
                decisionDefinitiveRepository.adjudicationsDuDossier(dossierId).stream().map(adjudicationMapper::toResponse).toList(),
                decisionDefinitiveRepository.condamnationsDuDossier(dossierId).stream().map(condamnationMapper::toResponse).toList());
    }

    /** FR-DEF-01/RG-DEF-01 : l'étape indiquée doit être {@code CLOTURE}. */
    @Transactional
    public AdjudicationResponse creerAdjudication(Long dossierId, CreerAdjudicationRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        EtapeProcedure etape = getEtapeOrThrow(dossierId, request.etapeId());
        if (etape.getStatutCycleVie() != StatutCycleVie.CLOTURE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Une adjudication ne peut être enregistrée que sur une étape CLOTURE (RG-DEF-01)");
        }

        Adjudication adjudication = decisionDefinitiveRepository.save(Adjudication.builder()
                .dossier(dossier)
                .etape(etape)
                .beneficiaire(request.beneficiaire())
                .statutComptabilisation(request.statutComptabilisation())
                .repriseProvision(request.repriseProvision())
                .restitutionSoulte(request.restitutionSoulte())
                .mutation(request.mutation())
                .reliquat(request.reliquat())
                .dateDecision(request.dateDecision())
                .build());

        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.ADJUDICATION_ENREGISTREE,
                Map.of("decisionId", adjudication.getId(), "beneficiaire", request.beneficiaire()));

        return adjudicationMapper.toResponse(adjudication);
    }

    /**
     * FR-DEF-02/RG-DEF-03/04 : aucune précondition d'étape n'est spécifiée par le texte fonctionnel
     * (contrairement à l'adjudication) — {@code etapeId} est donc optionnel (Q-59).
     */
    @Transactional
    public CondamnationResponse creerCondamnation(Long dossierId, CreerCondamnationRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        EtapeProcedure etape = request.etapeId() != null ? getEtapeOrThrow(dossierId, request.etapeId()) : null;

        CondamnationPecuniaire condamnation = decisionDefinitiveRepository.save(CondamnationPecuniaire.builder()
                .dossier(dossier)
                .etape(etape)
                .sens(request.sens())
                .naturePaiement(request.naturePaiement())
                .dateDecision(request.dateDecision())
                .build());

        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.CONDAMNATION_ENREGISTREE,
                Map.of("decisionId", condamnation.getId(), "sens", request.sens().name()));

        return condamnationMapper.toResponse(condamnation);
    }

    /** FR-DEF-02/US 8.2 : mise à jour du statut de paiement au fil du suivi. */
    @Transactional
    public CondamnationResponse modifierStatutCondamnation(Long id, ModifierStatutCondamnationRequest request) {
        CondamnationPecuniaire condamnation = decisionDefinitiveRepository.findById(id)
                .filter(CondamnationPecuniaire.class::isInstance)
                .map(CondamnationPecuniaire.class::cast)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Condamnation introuvable : " + id));

        condamnation.setStatutPaiement(request.statutPaiement());
        condamnation = (CondamnationPecuniaire) decisionDefinitiveRepository.save(condamnation);

        historiqueActionService.enregistrer(condamnation.getDossier().getId(), TypeActionHistorique.CONDAMNATION_STATUT_MODIFIE,
                Map.of("decisionId", id, "statutPaiement", request.statutPaiement().name()));

        return condamnationMapper.toResponse(condamnation);
    }

    private EtapeProcedure getEtapeOrThrow(Long dossierId, Long etapeId) {
        return etapeProcedureRepository.findById(etapeId)
                .filter(e -> e.getDossier().getId().equals(dossierId))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Étape introuvable pour ce dossier : " + etapeId));
    }

    private Dossier getDossierOrThrow(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + id));
    }
}
