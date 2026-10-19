package com.afriland.audiences.reporting.service;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.publication.domain.CompteRenduAudience;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.repository.CompteRenduAudienceRepository;
import com.afriland.audiences.reporting.web.dto.AudienceDuJourResponse;
import com.afriland.audiences.reporting.web.dto.CompteRenduAttenduResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hearing journal and expected reports: what happened on a given day, and what has still not been
 * reported.
 *
 * <p>No model change: {@code TENUE} is only set when the report is entered, so missing reports are
 * derived from existing data rather than duplicated in a flag. Lives in {@code reporting} because it
 * crosses hearings, cases, assignments, lawyer publications and file storage.
 */
@Service
@RequiredArgsConstructor
public class JournalAudiencesService {

    private final AudienceRepository audienceRepository;
    private final DossierJuristeRepository dossierJuristeRepository;
    private final CompteRenduAudienceRepository compteRenduAudienceRepository;
    private final MinioStorageService minioStorageService;

    /** Hearings of a given day, with what each side has reported. */
    @Transactional(readOnly = true)
    public PageResponse<AudienceDuJourResponse> journal(LocalDate date, Pageable pageable) {
        Page<Audience> audiences =
                audienceRepository.findByDatePlanifieeAndStatutNot(date, StatutAudience.ANNULEE, pageable);
        Map<Long, CompteRenduAudience> parAudience = comptesRendusAvocats(audiences.getContent());

        return PageResponse.of(audiences, audience -> {
            Dossier dossier = audience.getDossier();
            CompteRenduAudience avocat = parAudience.get(audience.getId());
            return new AudienceDuJourResponse(
                    audience.getId(),
                    dossier.getId(),
                    dossier.getReference(),
                    juridictionDe(audience, dossier),
                    audience.getEtape() == null ? null : audience.getEtape().getType().name(),
                    audience.getStatut(),
                    juristesAffectes(dossier.getId()),
                    audience.getCompteRendu(),
                    avocat == null ? null : avocat.getContenu(),
                    avocat == null || avocat.getAvocat() == null ? null : avocat.getAvocat().getId(),
                    urlPieceJointe(avocat));
        });
    }

    /**
     * Past hearings still missing the legal officer's report. The upper bound is capped at
     * yesterday (today's hearings are not late), so a fully future window returns an empty page
     * rather than an error.
     */
    @Transactional(readOnly = true)
    public PageResponse<CompteRenduAttenduResponse> comptesRendusAttendus(LocalDate dateDebut, LocalDate dateFin,
                                                                          Pageable pageable) {
        if (dateDebut.isAfter(dateFin)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début doit précéder la date de fin");
        }
        LocalDate aujourdHui = LocalDate.now();
        LocalDate fin = dateFin.isBefore(aujourdHui) ? dateFin : aujourdHui.minusDays(1);
        if (dateDebut.isAfter(fin)) {
            return new PageResponse<>(List.of(), 0, 0);
        }

        Page<Audience> enAttente = audienceRepository
                .findByStatutAndDatePlanifieeBetween(StatutAudience.PLANIFIEE, dateDebut, fin, pageable);
        Map<Long, CompteRenduAudience> parAudience = comptesRendusAvocats(enAttente.getContent());

        return PageResponse.of(enAttente, audience -> {
            Dossier dossier = audience.getDossier();
            return new CompteRenduAttenduResponse(
                    audience.getId(),
                    dossier.getId(),
                    dossier.getReference(),
                    juridictionDe(audience, dossier),
                    audience.getEtape() == null ? null : audience.getEtape().getType().name(),
                    audience.getDatePlanifiee(),
                    ChronoUnit.DAYS.between(audience.getDatePlanifiee(), aujourdHui),
                    juristesAffectes(dossier.getId()),
                    parAudience.containsKey(audience.getId()));
        });
    }

    /**
     * Download URL of the attachment to the lawyer's report, if any. A short-lived signed URL: the
     * storage path never leaves the server.
     */
    private String urlPieceJointe(CompteRenduAudience compteRendu) {
        if (compteRendu == null || compteRendu.getCheminStockage() == null) {
            return null;
        }
        return minioStorageService.urlTelechargement(compteRendu.getCheminStockage());
    }

    /**
     * Lawyers' reports for these hearings, in one query. Only validated filings count: a pending
     * one is not readable by any legal officer.
     */
    private Map<Long, CompteRenduAudience> comptesRendusAvocats(Collection<Audience> audiences) {
        if (audiences.isEmpty()) {
            return Map.of();
        }
        List<Long> identifiants = audiences.stream().map(Audience::getId).toList();
        return compteRenduAudienceRepository
                .findByAudienceIdInAndStatutValidation(identifiants, StatutPublication.VALIDE).stream()
                // A lawyer may file again: keep the most recent, which is authoritative.
                .collect(Collectors.toMap(
                        compteRendu -> compteRendu.getAudience().getId(),
                        Function.identity(),
                        (ancien, recent) -> recent));
    }

    /**
     * Court where the hearing takes place; the case's court is only the original one, accurate at
     * first instance only.
     */
    private static String juridictionDe(Audience audience, Dossier dossier) {
        return audience.getJuridiction() != null ? audience.getJuridiction() : dossier.getJuridictionSaisie();
    }

    private List<Long> juristesAffectes(Long dossierId) {
        return dossierJuristeRepository.findByDossierId(dossierId).stream()
                .map(affectation -> affectation.getUtilisateur().getId())
                .toList();
    }
}
