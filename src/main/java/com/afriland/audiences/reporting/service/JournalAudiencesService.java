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
 * Le journal des audiences et les comptes rendus attendus (observation métier du 2026-09-25).
 *
 * <p><b>Le besoin.</b> « Avoir les comptes rendus de toutes les audiences tenues un jour précis,
 * pour une vue d'ensemble, et établir les responsabilités. » Deux questions, en réalité : ce qui
 * s'est passé tel jour, et ce qui n'a toujours pas été rapporté. Elles ne se regardent pas au même
 * rythme — l'une chaque matin, l'autre de loin en loin — d'où deux lectures distinctes.
 *
 * <p><b>Rien n'a été ajouté au modèle.</b> Le statut {@code TENUE} n'étant posé qu'à la saisie du
 * compte rendu, les manquements se lisent dans les données existantes. Créer un indicateur
 * « compte rendu manquant » aurait dupliqué une vérité que le statut porte déjà, avec le risque
 * ordinaire des duplications : diverger.
 *
 * <p><b>Ce module vit dans {@code reporting}</b>, seul endroit qui traverse légitimement les
 * modules métier : le journal croise l'audience, le dossier, ses affectations, les publications des
 * avocats et le stockage de leurs pièces.
 */
@Service
@RequiredArgsConstructor
public class JournalAudiencesService {

    private final AudienceRepository audienceRepository;
    private final DossierJuristeRepository dossierJuristeRepository;
    private final CompteRenduAudienceRepository compteRenduAudienceRepository;
    private final MinioStorageService minioStorageService;

    /** Les audiences d'une journée, avec ce qui en a été rapporté de part et d'autre. */
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
                    dossier.getJuridictionSaisie(),
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
     * Les audiences passées dont le compte rendu du juriste manque encore.
     *
     * <p>La borne haute est ramenée à <b>hier</b> : une audience du jour n'est pas en retard, et la
     * compter comme telle ferait apparaître chaque matin des manquements imaginaires. Une fenêtre
     * entièrement future ne renvoie donc rien, plutôt qu'une erreur — il n'y a simplement rien à
     * reprocher.
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
                    dossier.getJuridictionSaisie(),
                    audience.getEtape() == null ? null : audience.getEtape().getType().name(),
                    audience.getDatePlanifiee(),
                    ChronoUnit.DAYS.between(audience.getDatePlanifiee(), aujourdHui),
                    juristesAffectes(dossier.getId()),
                    parAudience.containsKey(audience.getId()));
        });
    }

    /**
     * L'adresse de téléchargement de la pièce jointe au compte rendu de l'avocat, s'il en a joint une.
     *
     * <p>Une adresse signée, produite à la demande et de durée limitée, comme partout ailleurs dans
     * l'application : le chemin de stockage ne sort jamais du serveur.
     */
    private String urlPieceJointe(CompteRenduAudience compteRendu) {
        if (compteRendu == null || compteRendu.getCheminStockage() == null) {
            return null;
        }
        return minioStorageService.urlTelechargement(compteRendu.getCheminStockage());
    }

    /**
     * Les comptes rendus d'avocats de ces audiences, en une requête.
     *
     * <p>Seuls les dépôts <b>validés</b> comptent : un compte rendu en attente de validation n'est
     * lisible d'aucun juriste, et le présenter comme rendu ferait croire à une information
     * disponible.
     */
    private Map<Long, CompteRenduAudience> comptesRendusAvocats(Collection<Audience> audiences) {
        if (audiences.isEmpty()) {
            return Map.of();
        }
        List<Long> identifiants = audiences.stream().map(Audience::getId).toList();
        return compteRenduAudienceRepository
                .findByAudienceIdInAndStatutValidation(identifiants, StatutPublication.VALIDE).stream()
                // Un même avocat peut redéposer : on garde le plus récent, celui qui fait foi.
                .collect(Collectors.toMap(
                        compteRendu -> compteRendu.getAudience().getId(),
                        Function.identity(),
                        (ancien, recent) -> recent));
    }

    private List<Long> juristesAffectes(Long dossierId) {
        return dossierJuristeRepository.findByDossierId(dossierId).stream()
                .map(affectation -> affectation.getUtilisateur().getId())
                .toList();
    }
}
