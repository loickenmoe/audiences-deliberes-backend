package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.dossier.mapper.HistoriqueActionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Audit consultation (DJ and DJA): cross-case queries such as "what did X do last month".
 *
 * <p>{@link Perimetre} is just one filter; filtering by person is allowed by NF-CONF-01. Protection
 * relies on the role guard and on the fact that every consultation is itself recorded.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    /** Newest first, `id` breaking ties. */
    private static final Sort TRI = Sort.by(Sort.Direction.DESC, "dateAction").and(Sort.by(Sort.Direction.DESC, "id"));

    private final HistoriqueActionRepository historiqueActionRepository;
    private final HistoriqueActionMapper historiqueActionMapper;
    private final HistoriqueActionService historiqueActionService;

    @Transactional
    public AuditResponse consulter(FiltresAudit filtres, int page, int size) {
        if (filtres.perimetre() == Perimetre.UN_DOSSIER && filtres.dossierId() == null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Le périmètre « un dossier » exige l'identifiant du dossier");
        }
        if (filtres.dateDebut() != null && filtres.dateFin() != null
                && filtres.dateDebut().isAfter(filtres.dateFin())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début ne peut pas être postérieure à la date de fin");
        }

        /*
         * Recorded before reading, so the trace exists even if the query fails, and only for the
         * first page: paging through results is not a new consultation.
         */
        if (page == 0) {
            historiqueActionService.enregistrer(null, TypeActionHistorique.AUDIT_CONSULTE,
                    criteres(filtres));
        }

        Page<HistoriqueAction> resultat = historiqueActionRepository.findAll(
                HistoriqueActionSpecifications.withFilters(filtres.perimetre(), filtres.dossierId(),
                        filtres.utilisateurId(), filtres.code(), filtres.dateDebut(), filtres.dateFin()),
                PageRequest.of(page, size, TRI));

        return new AuditResponse(
                resultat.getContent().stream().map(historiqueActionMapper::toResponse).toList(),
                resultat.getTotalPages(),
                resultat.getTotalElements(),
                historiqueActionRepository.actionsPresentes().stream()
                        .map(AuditResponse.OptionAction::de)
                        .toList());
    }

    /** The criteria used, as given; absent filters are omitted. */
    private Map<String, Object> criteres(FiltresAudit filtres) {
        Map<String, Object> criteres = new LinkedHashMap<>();
        criteres.put("perimetre", filtres.perimetre().name());
        if (filtres.dossierId() != null) criteres.put("dossierId", filtres.dossierId());
        // Named `auteurRecherche`, not `utilisateurId`, which already identifies the consulting user.
        if (filtres.utilisateurId() != null) criteres.put("auteurRecherche", filtres.utilisateurId());
        if (filtres.code() != null) criteres.put("codeRecherche", filtres.code().name());
        if (filtres.dateDebut() != null) criteres.put("dateDebut", filtres.dateDebut().toString());
        if (filtres.dateFin() != null) criteres.put("dateFin", filtres.dateFin().toString());
        return criteres;
    }
}
