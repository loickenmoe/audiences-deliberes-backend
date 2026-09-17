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
 * Consultation de l'audit (Q-112) — <b>DJ et DJA</b>.
 *
 * <p><b>Ce qu'il apporte par rapport au journal de Q-110.</b> Celui-ci répondait à une question de
 * <i>couverture</i> : des lignes existaient sans aucun point de lecture. Il restait une question de
 * <i>point d'entrée</i> — « qu'a fait X le mois dernier ? », « toutes les suppressions du
 * trimestre ? », « que s'est-il passé entre le 1er et le 15 ? ». Aucune ne part d'un dossier, et
 * aucune n'était donc possible. C'est ce que cet écran ouvre.
 *
 * <p><b>Le périmètre devient un filtre, et c'est un changement assumé.</b> En Q-110, l'exclusivité
 * de {@link Perimetre} était structurelle : ouvrir la vue transversale devait être une décision, pas
 * l'effet d'un paramètre oublié. Cette décision est prise — le filtre par personne est autorisé
 * (NF-CONF-01, 2026-09-17) — et le périmètre redevient donc ce qu'il est, un critère. Ce qui protège
 * désormais n'est plus la forme du code mais deux garde-fous explicites : la garde de rôle, et le
 * fait que <b>toute consultation est elle-même historisée</b>.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    /** Du plus récent au plus ancien, `id` départageant les ex æquo (Q-92). */
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
         * Historisé **avant** la lecture et sur la première page seulement. Avant, pour que la trace
         * existe même si la requête échoue ensuite. Sur la première page seulement, parce que
         * feuilleter un résultat n'est pas une nouvelle consultation : compter chaque page
         * produirait un bruit qui noierait ce qu'on cherche à voir.
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

    /**
     * Les critères employés, tels quels. Un filtre absent n'est pas consigné : une trace qui
     * énumérerait des « null » se lirait moins bien qu'une trace qui ne dit que ce qui a servi.
     */
    private Map<String, Object> criteres(Perimetre perimetre, Long dossierId, Long utilisateurId,
                                            TypeActionHistorique code, LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> criteres = new LinkedHashMap<>();
        criteres.put("perimetre", perimetre.name());
        if (dossierId != null) criteres.put("dossierId", dossierId);
        // Nommé `auteurRecherche` et non `utilisateurId` : ce dernier désigne déjà, sur la ligne
        // d'historique, celui qui a consulté. Les confondre rendrait la trace illisible.
        if (utilisateurId != null) criteres.put("auteurRecherche", utilisateurId);
        if (code != null) criteres.put("codeRecherche", code.name());
        if (dateDebut != null) criteres.put("dateDebut", dateDebut.toString());
        if (dateFin != null) criteres.put("dateFin", dateFin.toString());
        return criteres;
    }
}
