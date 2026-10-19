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

import java.time.LocalDate;
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
    public AuditResponse consulter(Perimetre perimetre, Long dossierId, Long utilisateurId,
                                      TypeActionHistorique code, LocalDate dateDebut, LocalDate dateFin,
                                      int page, int size) {
        if (perimetre == Perimetre.UN_DOSSIER && dossierId == null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Le périmètre « un dossier » exige l'identifiant du dossier");
        }
        if (dateDebut != null && dateFin != null && dateDebut.isAfter(dateFin)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début ne peut pas être postérieure à la date de fin");
        }

        /*
         * Recorded before reading, so the trace exists even if the query fails, and only for the
         * first page: paging through results is not a new consultation.
         */
        if (page == 0) {
            historiqueActionService.enregistrer(null, TypeActionHistorique.AUDIT_CONSULTE,
                    criteres(perimetre, dossierId, utilisateurId, code, dateDebut, dateFin));
        }

        Page<HistoriqueAction> resultat = historiqueActionRepository.findAll(
                HistoriqueActionSpecifications.withFilters(perimetre, dossierId, utilisateurId, code,
                        dateDebut, dateFin),
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
    private Map<String, Object> criteres(Perimetre perimetre, Long dossierId, Long utilisateurId,
                                            TypeActionHistorique code, LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> criteres = new LinkedHashMap<>();
        criteres.put("perimetre", perimetre.name());
        if (dossierId != null) criteres.put("dossierId", dossierId);
        // Named `auteurRecherche`, not `utilisateurId`, which already identifies the consulting user.
        if (utilisateurId != null) criteres.put("auteurRecherche", utilisateurId);
        if (code != null) criteres.put("codeRecherche", code.name());
        if (dateDebut != null) criteres.put("dateDebut", dateDebut.toString());
        if (dateFin != null) criteres.put("dateFin", dateFin.toString());
        return criteres;
    }
}
