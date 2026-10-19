package com.afriland.audiences.audience.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
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
import com.afriland.audiences.audience.web.dto.NoteAvocatRequest;
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

/** Hearings (RG-AUD-01/03/06/07). */
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
        // RG-AUD-08: a hearing can be recorded on the same day or afterwards, so a hearing held
        // without being entered can still be regularised.
        // The lower bound is the case opening date: a hearing cannot precede its procedure. This
        // catches typos (wrong year) without imposing an arbitrary delay.
        LocalDate ouvertureDossier = dossier.getCreatedAt() == null ? null : dossier.getCreatedAt().toLocalDate();
        if (ouvertureDossier != null && request.datePlanifiee().isBefore(ouvertureDossier)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Une audience ne peut pas précéder l'ouverture du dossier, le " + ouvertureDossier
                            + " (RG-AUD-08)");
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
                // By default the hearing takes place where the case was filed (first instance);
                // appeals or cassation specify their own court.
                .juridiction(juridictionOuCelleDuDossier(request.juridiction(), dossier))
                .statut(StatutAudience.PLANIFIEE)
                .build());

        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.AUDIENCE_PLANIFIEE,
                Map.of("audienceId", audience.getId(), "datePlanifiee", request.datePlanifiee().toString()));

        if (doublon) {
            signalerDoublonAudience(dossier, audience);
        }

        return audienceMapper.toResponse(audience);
    }

    private static String juridictionOuCelleDuDossier(String demandee, Dossier dossier) {
        return demandee == null || demandee.isBlank() ? dossier.getJuridictionSaisie() : demandee.strip();
    }

    /** Duplicate warning for RG-AUD-06, same mechanism as for cases. */
    private void signalerDoublonAudience(Dossier dossier, Audience audience) {
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue("DJ");
        String declencheur = "Doublon d'audience confirmé sur le dossier " + dossier.getReference()
                + " (RG-AUD-06)";
        destinataires.forEach(dj -> alerteService.declencher(TypeAlerte.DOUBLON_AUDIENCE,
                dossier.getId(), dj, declencheur));
        historiqueActionService.enregistrer(dossier.getId(), TypeActionHistorique.AUDIENCE_DOUBLON_CONFIRME,
                Map.of("audienceId", audience.getId()));
    }

    /**
     * Note on a hearing addressed to the assigned lawyer.
     * <p><b>Separate from the report</b>, which stays internal and may assess the lawyer's own work
     * (RG-INT-07).
     * <p>Can be written at any time, including before the hearing. An empty note is cleared.
     */
    @Transactional
    public AudienceResponse modifierNoteAvocat(Long audienceId, NoteAvocatRequest request) {
        Audience audience = getAudienceOrThrow(audienceId);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        audience.setNoteAvocat(note);
        audience = audienceRepository.save(audience);
        historiqueActionService.enregistrer(audience.getDossier().getId(),
                TypeActionHistorique.AUDIENCE_NOTE_AVOCAT_MODIFIEE, Map.of("audienceId", audience.getId()));
        return audienceMapper.toResponse(audience);
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
        // The hearing was held on its planned date, not on the day the report is entered. An
        // adjournment is a reasoned cancellation followed by a new scheduling, so the planned date
        // of a non-cancelled hearing is the date it was held. The activity report counts by this
        // date.
        audience.setDateTenue(audience.getDatePlanifiee());
        audience.setStatut(StatutAudience.TENUE);
        audience = audienceRepository.save(audience);

        historiqueActionService.enregistrer(audience.getDossier().getId(), TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE,
                Map.of("audienceId", audience.getId()));

        return audienceMapper.toResponse(audience);
    }

    /**
     * Cancels a scheduled hearing (adjournment by the court or wrong date); a reason is mandatory.
     * The hearing leaves the calendar and its exports, no longer gets J-7/J-3/J-1 reminders (which
     * target {@code PLANIFIEE} only) and no longer counts as a duplicate. Postponing means
     * cancelling then scheduling the new date.
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

        historiqueActionService.enregistrer(audience.getDossier().getId(), TypeActionHistorique.AUDIENCE_ANNULEE,
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

    /** "Hearings" tab of the case page; 404 if the case does not exist. Paginated. */
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
                audience.getId(), dossier.getId(), dossier.getReference(),
                // The hearing's own court: an appealed case no longer sits where it was filed.
                audience.getJuridiction() != null ? audience.getJuridiction() : dossier.getJuridictionSaisie(),
                audience.getEtape() != null ? audience.getEtape().getType().name() : null,
                juristesAffectes, avocatsAffectes, dossier.getCodeAgenceOrigine(), dossier.getClient().getNom(),
                dossier.getRisqueEncouru(), audience.getDatePlanifiee());
    }
}
