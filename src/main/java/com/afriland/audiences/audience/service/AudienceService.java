package com.afriland.audiences.audience.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.PeriodeCalendrier;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.mapper.AudienceMapper;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.audience.web.dto.AnnulerAudienceRequest;
import com.afriland.audiences.audience.web.dto.AudienceResponse;
import com.afriland.audiences.audience.web.dto.CalendrierEntreeResponse;
import com.afriland.audiences.audience.web.dto.EnregistrerCompteRenduRequest;
import com.afriland.audiences.audience.web.dto.PlanifierAudienceRequest;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import com.afriland.audiences.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Épic 2 (RG-AUD-01/03/06/07) — cf. FUNCTIONAL_MAP.md §2, API_IMPLEMENTATION_STATUS.md §3. */
@Service
@RequiredArgsConstructor
public class AudienceService {

    private final AudienceRepository audienceRepository;
    private final DossierRepository dossierRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;
    private final DossierJuristeRepository dossierJuristeRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final AudienceMapper audienceMapper;

    @Transactional
    public AudienceResponse planifierAudience(Long dossierId, PlanifierAudienceRequest request, boolean forcer) {
        Dossier dossier = getDossierOrThrow(dossierId);
        EtapeProcedure etape = etapeProcedureRepository.findById(request.etapeId())
                .filter(e -> e.getDossier().getId().equals(dossierId))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Étape introuvable pour ce dossier : " + request.etapeId()));

        if (etape.getStatutCycleVie() != StatutCycleVie.EN_COURS) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Une audience ne peut être planifiée que sur une étape EN_COURS (RG-AUD-01)");
        }
        if (!request.datePlanifiee().isAfter(LocalDate.now())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date planifiée doit être future (RG-AUD-07)");
        }

        boolean doublon = audienceRepository.existsByEtapeIdAndDatePlanifieeAndStatutNot(
                request.etapeId(), request.datePlanifiee(), StatutAudience.ANNULEE);
        if (doublon && !forcer) {
            throw ApiException.conflict(ErrorCodes.DOUBLON_AUDIENCE,
                    "Une audience existe déjà pour cette étape à cette date ; ajoutez ?forcer=true pour confirmer (RG-AUD-06)");
        }

        Audience audience = audienceRepository.save(Audience.builder()
                .dossier(dossier)
                .etape(etape)
                .datePlanifiee(request.datePlanifiee())
                .statut(StatutAudience.PLANIFIEE)
                .build());

        historiqueActionService.enregistrer(dossierId, "Planification d'une audience",
                Map.of("audienceId", audience.getId(), "datePlanifiee", request.datePlanifiee().toString()));

        if (doublon) {
            signalerDoublonAudience(dossier, audience);
        }

        return audienceMapper.toResponse(audience);
    }

    /** Q-12/Q-36 (dossier), même mécanisme pour le volet audience de RG-AUD-06. */
    private void signalerDoublonAudience(Dossier dossier, Audience audience) {
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue("DJ");
        String declencheur = "Doublon d'audience confirmé sur le dossier " + dossier.getReference()
                + " (RG-AUD-06)";
        destinataires.forEach(dj -> alerteService.declencher(TypeAlerte.DOUBLON_AUDIENCE,
                dossier.getId(), dj, declencheur));
        historiqueActionService.enregistrer(dossier.getId(), "Doublon d'audience confirmé",
                Map.of("audienceId", audience.getId()));
    }

    @Transactional
    public AudienceResponse enregistrerCompteRendu(Long audienceId, EnregistrerCompteRenduRequest request) {
        Audience audience = getAudienceOrThrow(audienceId);
        if (audience.getStatut() == StatutAudience.ANNULEE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Une audience annulée ne reçoit pas de compte rendu (Q-76)");
        }
        if (audience.getDatePlanifiee().isAfter(LocalDate.now())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Le compte rendu ne peut être saisi qu'après la tenue de l'audience (RG-AUD-01)");
        }
        audience.setCompteRendu(request.compteRendu());
        audience.setDateTenue(LocalDate.now());
        audience.setStatut(StatutAudience.TENUE);
        audience = audienceRepository.save(audience);

        historiqueActionService.enregistrer(audience.getDossier().getId(), "Compte rendu d'audience enregistré",
                Map.of("audienceId", audience.getId()));

        return audienceMapper.toResponse(audience);
    }

    /**
     * Q-76 : annulation d'une audience planifiée — renvoi prononcé par le tribunal ou date erronée.
     * Le motif est obligatoire. L'audience sort du calendrier et de ses exports, ne reçoit plus ses
     * rappels J-7/J-3/J-1 (qui ne visent que {@code PLANIFIEE}) et ne compte plus comme doublon.
     * Un report consiste à annuler, puis à planifier la nouvelle date.
     */
    @Transactional
    public AudienceResponse annulerAudience(Long audienceId, AnnulerAudienceRequest request) {
        Audience audience = getAudienceOrThrow(audienceId);
        if (audience.getStatut() != StatutAudience.PLANIFIEE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Seule une audience planifiée peut être annulée (statut actuel : " + audience.getStatut() + ")");
        }
        String motif = request.motif().trim();
        audience.setStatut(StatutAudience.ANNULEE);
        audience.setMotifAnnulation(motif);
        audience.setDateAnnulation(LocalDateTime.now());
        audience = audienceRepository.save(audience);

        historiqueActionService.enregistrer(audience.getDossier().getId(), "Annulation d'une audience",
                Map.of("audienceId", audience.getId(), "datePlanifiee", audience.getDatePlanifiee().toString(),
                        "motif", motif));

        return audienceMapper.toResponse(audience);
    }

    private Audience getAudienceOrThrow(Long id) {
        return audienceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Audience introuvable : " + id));
    }

    private Dossier getDossierOrThrow(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + id));
    }

    /**
     * M15 (Q-68) : onglet « Audiences » de la fiche dossier — 404 si le dossier n'existe pas.
     * Paginé depuis Q-100 : une procédure suivie sur plusieurs années accumule ses audiences.
     */
    @Transactional(readOnly = true)
    public PageResponse<AudienceResponse> listerParDossier(Long dossierId, Pageable pageable) {
        getDossierOrThrow(dossierId);
        return PageResponse.of(audienceRepository.findByDossierIdOrderByDatePlanifieeDesc(dossierId, pageable),
                audienceMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<CalendrierEntreeResponse> calendrier(PeriodeCalendrier periode, LocalDate dateDebut) {
        LocalDate dateFin = periode.dateFin(dateDebut);
        return audienceRepository.findByDatePlanifieeBetweenAndStatutNot(dateDebut, dateFin, StatutAudience.ANNULEE).stream()
                .map(this::construireEntreeCalendrier)
                .toList();
    }

    private CalendrierEntreeResponse construireEntreeCalendrier(Audience audience) {
        Dossier dossier = audience.getDossier();
        List<Long> juristesAffectes = dossierJuristeRepository.findByDossierId(dossier.getId()).stream()
                .map(dj -> dj.getUtilisateur().getId()).toList();
        List<Long> avocatsAffectes = dossierAvocatRepository.findByDossierId(dossier.getId()).stream()
                .map(da -> da.getAvocat().getId()).toList();
        return new CalendrierEntreeResponse(
                audience.getId(), dossier.getId(), dossier.getReference(), dossier.getJuridictionSaisie(),
                audience.getEtape() != null ? audience.getEtape().getType().name() : null,
                juristesAffectes, avocatsAffectes, dossier.getCodeAgenceOrigine(), dossier.getClient().getNom(),
                dossier.getRisqueEncouru(), audience.getDatePlanifiee());
    }
}
