package com.afriland.audiences.audience.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.audience.domain.Alarme;
import com.afriland.audiences.audience.domain.StatutAlarme;
import com.afriland.audiences.audience.mapper.AlarmeMapper;
import com.afriland.audiences.audience.repository.AlarmeRepository;
import com.afriland.audiences.audience.web.dto.AlarmeResponse;
import com.afriland.audiences.audience.web.dto.CreerAlarmeRequest;
import com.afriland.audiences.audience.web.dto.ReprogrammerAlarmeRequest;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import com.afriland.audiences.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/** Alarms (FR-AUD-03/04). */
@Service
@RequiredArgsConstructor
public class AlarmeService {

    private final AlarmeRepository alarmeRepository;
    private final DossierRepository dossierRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;
    private final HistoriqueActionService historiqueActionService;
    private final CurrentUserService currentUserService;
    private final AlarmeMapper alarmeMapper;

    @Transactional
    public AlarmeResponse creerAlarme(Long dossierId, CreerAlarmeRequest request) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + dossierId));
        EtapeProcedure etape = etapeProcedureRepository.findById(request.etapeId())
                .filter(e -> e.getDossier().getId().equals(dossierId))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Étape introuvable pour ce dossier : " + request.etapeId()));
        if (!request.dateEcheance().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "L'échéance d'une alarme doit être future");
        }

        Utilisateur auteur = currentUserService.getCurrentUser();
        Alarme alarme = alarmeRepository.save(Alarme.builder()
                .dossier(dossier)
                .etape(etape)
                .objet(request.objet())
                .dateEcheance(request.dateEcheance())
                .statut(StatutAlarme.ACTIVE)
                .createdBy(auteur)
                .build());

        historiqueActionService.enregistrerPour(auteur, dossierId, TypeActionHistorique.ALARME_CREEE,
                Map.of("alarmeId", alarme.getId(), "objet", request.objet()));

        return alarmeMapper.toResponse(alarme);
    }

    @Transactional
    public AlarmeResponse reprogrammerAlarme(Long alarmeId, ReprogrammerAlarmeRequest request) {
        Alarme ancienne = alarmeRepository.findById(alarmeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Alarme introuvable : " + alarmeId));
        if (ancienne.getStatut() != StatutAlarme.ACTIVE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Cette alarme a déjà été reprogrammée ou traitée");
        }
        if (!request.dateEcheance().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "La nouvelle échéance doit être future");
        }

        Utilisateur auteur = currentUserService.getCurrentUser();
        ancienne.setStatut(StatutAlarme.TRAITEE);
        ancienne.setDateTraitement(LocalDateTime.now());
        alarmeRepository.save(ancienne);

        String objet = StringUtils.hasText(request.objet()) ? request.objet() : ancienne.getObjet();
        Alarme nouvelle = alarmeRepository.save(Alarme.builder()
                .dossier(ancienne.getDossier())
                .etape(ancienne.getEtape())
                .objet(objet)
                .dateEcheance(request.dateEcheance())
                .statut(StatutAlarme.ACTIVE)
                .alarmePrecedente(ancienne)
                .createdBy(auteur)
                .build());

        historiqueActionService.enregistrerPour(auteur, ancienne.getDossier().getId(), TypeActionHistorique.ALARME_REPROGRAMMEE,
                Map.of("ancienneAlarmeId", alarmeId, "nouvelleAlarmeId", nouvelle.getId()));

        return alarmeMapper.toResponse(nouvelle);
    }

    /**
     * Marks the alarm as handled: it becomes {@code TRAITEE} without replacement and leaves
     * due-date monitoring.
     */
    @Transactional
    public AlarmeResponse traiterAlarme(Long alarmeId) {
        Alarme alarme = alarmeRepository.findById(alarmeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Alarme introuvable : " + alarmeId));
        if (alarme.getStatut() != StatutAlarme.ACTIVE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Cette alarme a déjà été reprogrammée ou traitée");
        }

        Utilisateur auteur = currentUserService.getCurrentUser();
        alarme.setStatut(StatutAlarme.TRAITEE);
        alarme.setDateTraitement(LocalDateTime.now());
        alarme = alarmeRepository.save(alarme);

        historiqueActionService.enregistrerPour(auteur, alarme.getDossier().getId(), TypeActionHistorique.ALARME_TRAITEE,
                Map.of("alarmeId", alarmeId, "objet", alarme.getObjet()));

        return alarmeMapper.toResponse(alarme);
    }

    /** Alarms of a case; 404 if the case does not exist. */
    @Transactional(readOnly = true)
    public PageResponse<AlarmeResponse> listerParDossier(Long dossierId, Pageable pageable) {
        if (!dossierRepository.existsById(dossierId)) {
            throw ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + dossierId);
        }
        return PageResponse.of(alarmeRepository.findByDossierIdOrderByDateEcheanceAsc(dossierId, pageable),
                alarmeMapper::toResponse);
    }
}
