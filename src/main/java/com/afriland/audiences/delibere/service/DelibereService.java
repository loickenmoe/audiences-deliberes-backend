package com.afriland.audiences.delibere.service;

import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.domain.Prorogation;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.domain.StatutExpedition;
import com.afriland.audiences.delibere.mapper.DelibereMapper;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.delibere.repository.ProrogationRepository;
import com.afriland.audiences.delibere.web.dto.CreerProrogationRequest;
import com.afriland.audiences.delibere.web.dto.DelibereResponse;
import com.afriland.audiences.delibere.web.dto.EnregistrerDelibereRequest;
import com.afriland.audiences.delibere.web.dto.ExpeditionRequest;
import com.afriland.audiences.delibere.web.dto.ProrogationResponse;
import com.afriland.audiences.delibere.web.dto.RabattementRequest;
import com.afriland.audiences.delibere.web.dto.RecoursResponse;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.mapper.EtapeProcedureMapper;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Épic 3 (RG-DEL-01/02/03/04/06/07) — cf. FUNCTIONAL_MAP.md §3, API_IMPLEMENTATION_STATUS.md §4. */
@Service
@RequiredArgsConstructor
public class DelibereService {

    private final DelibereRepository delibereRepository;
    private final ProrogationRepository prorogationRepository;
    private final DossierRepository dossierRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;
    private final EtapeProcedureMapper etapeProcedureMapper;
    private final UtilisateurRepository utilisateurRepository;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final ConfigurationService configurationService;
    private final DelibereMapper delibereMapper;

    @Transactional
    public DelibereResponse enregistrerDelibere(Long dossierId, EnregistrerDelibereRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        EtapeProcedure etape = etapeProcedureRepository.findById(request.etapeId())
                .filter(e -> e.getDossier().getId().equals(dossierId))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Étape introuvable pour ce dossier : " + request.etapeId()));

        if (etape.getStatutCycleVie() != StatutCycleVie.EN_DELIBERE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Un délibéré ne peut être enregistré que sur une étape EN_DELIBERE (RG-DEL-01)");
        }
        validerEcheanceRecours(request.resultat(), request.dateDeliberee(), request.dateEcheanceRecours());

        Delibere delibere = delibereRepository.save(Delibere.builder()
                .dossier(dossier)
                .etape(etape)
                .dateDeliberee(request.dateDeliberee())
                .resultat(request.resultat())
                .statutExpedition(StatutExpedition.A_LEVER)
                .dateEcheanceRecours(request.dateEcheanceRecours())
                .build());

        etape.setStatutCycleVie(StatutCycleVie.DELIBERE_VIDE);
        etapeProcedureRepository.save(etape);

        historiqueActionService.enregistrer(dossierId, "Enregistrement d'un délibéré",
                Map.of("delibereId", delibere.getId(), "resultat", request.resultat().name()));

        return delibereMapper.toResponse(delibere);
    }

    /** RG-DEL-01 : seuls DEFAVORABLE/MIXTE ouvrent une voie de recours (Q-42) — échéance requise et postérieure au délibéré, absente sinon. */
    private void validerEcheanceRecours(ResultatDelibere resultat, LocalDate dateDeliberee, LocalDateTime dateEcheanceRecours) {
        boolean voieDeRecours = resultat != ResultatDelibere.FAVORABLE;
        if (voieDeRecours && dateEcheanceRecours == null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "L'échéance du délai de recours est requise pour un résultat " + resultat);
        }
        if (!voieDeRecours && dateEcheanceRecours != null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Un délibéré FAVORABLE n'ouvre aucune voie de recours : aucune échéance ne doit être fournie");
        }
        if (voieDeRecours && !dateEcheanceRecours.isAfter(dateDeliberee.atStartOfDay())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "L'échéance du délai de recours doit être postérieure à la date du délibéré");
        }
    }

    /**
     * RG-DEL-06 : aucune contrainte sur le statut de l'étape — {@code prorogation.delibere_id}
     * référence nécessairement un délibéré déjà enregistré (schéma M2), lui-même déjà passé par
     * {@link #enregistrerDelibere} (qui a fait basculer l'étape en {@code DELIBERE_VIDE}) ; « l'étape
     * reste EN_DELIBERE » (FR-DEL-04) décrit donc l'absence d'effet de bord de cette opération sur
     * l'étape, pas une précondition d'entrée (Q-44, DECISIONS_AND_OPEN_QUESTIONS.md).
     */
    @Transactional
    public ProrogationResponse proroger(Long delibereId, CreerProrogationRequest request) {
        Delibere delibere = getDelibereOrThrow(delibereId);

        int compteur = (int) prorogationRepository.countByDelibereId(delibereId) + 1;
        Prorogation prorogation = prorogationRepository.save(Prorogation.builder()
                .delibere(delibere)
                .date(request.date())
                .motif(request.motif())
                .compteur(compteur)
                .build());

        historiqueActionService.enregistrer(delibere.getDossier().getId(), "Prorogation d'un délibéré",
                Map.of("delibereId", delibereId, "compteur", compteur));

        int seuil = Integer.parseInt(configurationService.getOrThrow("SEUIL_PROROGATIONS").getValeur());
        boolean alerte = compteur > seuil;
        if (alerte) {
            signalerProrogation(delibere, compteur);
        }

        return new ProrogationResponse(prorogation.getId(), delibereId, prorogation.getDate(), prorogation.getMotif(),
                compteur, alerte, prorogation.getCreatedAt());
    }

    /** RG-DEL-06/RG-ALR-01 (Q-18) : franchissement du seuil signalé une seule fois, au DJ et au DJA. */
    private void signalerProrogation(Delibere delibere, int compteur) {
        String marqueur = "[delibereId:" + delibere.getId() + "] ";
        String declencheur = marqueur + "Seuil de prorogations dépassé (" + compteur + ") pour le dossier "
                + delibere.getDossier().getReference() + " (RG-DEL-06).";
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue("DJ");
        destinataires.addAll(utilisateurRepository.findByProfilCodeAndActifTrue("DJA"));
        for (Utilisateur destinataire : destinataires) {
            if (!alerteService.aDejaEteDeclenchee(TypeAlerte.PROROGATION, destinataire.getId(), marqueur)) {
                alerteService.declencher(TypeAlerte.PROROGATION, delibere.getDossier().getId(), destinataire, declencheur);
            }
        }
    }

    /**
     * RG-DEL-07/FR-DEL-05 : le délibéré est rabattu tant que l'étape est encore {@code DELIBERE_VIDE}
     * et qu'aucun recours n'a été exercé (transition distincte de {@code DELIBERE_VIDE → EN_COURS}
     * via l'endpoint générique, réservée à l'exercice d'un recours — Q-42). Aucune étape suivante
     * n'est ouverte : contrairement au recours, le rabattement rejoue la même étape.
     */
    @Transactional
    public EtapeProcedureResponse rabattre(Long delibereId, RabattementRequest request) {
        Delibere delibere = getDelibereOrThrow(delibereId);
        EtapeProcedure etape = delibere.getEtape();
        if (etape == null || etape.getStatutCycleVie() != StatutCycleVie.DELIBERE_VIDE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Un rabattement ne peut être enregistré que sur une étape DELIBERE_VIDE (RG-DEL-07)");
        }

        etape.setStatutCycleVie(StatutCycleVie.EN_COURS);
        etape = etapeProcedureRepository.save(etape);

        historiqueActionService.enregistrer(delibere.getDossier().getId(), "Rabattement du délibéré",
                Map.of("delibereId", delibereId, "date", request.date().toString(), "motif", request.motif()));

        return etapeProcedureMapper.toResponse(etape);
    }

    /** FR-DEL-02/Q-19 : consultation seule, aucune création d'alerte via ce GET. */
    @Transactional(readOnly = true)
    public RecoursResponse consulterRecours(Long delibereId) {
        Delibere delibere = getDelibereOrThrow(delibereId);
        EtapeProcedure etape = delibere.getEtape();
        StatutCycleVie statut = etape != null ? etape.getStatutCycleVie() : null;
        boolean cloture = statut == StatutCycleVie.CLOTURE;
        boolean recoursExerce = recoursAEteExerce(delibere.getDossier().getId(), etape);
        boolean delaiExpire = delibere.getDateEcheanceRecours() != null
                && !delibere.getDateEcheanceRecours().isAfter(LocalDateTime.now());

        return new RecoursResponse(delibereId, delibere.getDateEcheanceRecours(), recoursExerce, delaiExpire, cloture);
    }

    /**
     * Un recours a été exercé sur cette étape si l'étape suivante (RECOURS_1/2) a été ouverte
     * automatiquement (Q-42) — signal distinct d'un rabattement, qui rejoue la même étape sans
     * jamais ouvrir la suivante (cf. {@code EtapeTransitionValidator#estExerciceDeRecours}).
     */
    private boolean recoursAEteExerce(Long dossierId, EtapeProcedure etape) {
        if (etape == null || etape.getType() == TypeEtape.RECOURS_2) {
            return false;
        }
        return etapeProcedureRepository.existsByDossierIdAndType(dossierId, etape.getType().suivante());
    }

    @Transactional
    public DelibereResponse modifierExpedition(Long delibereId, ExpeditionRequest request) {
        Delibere delibere = getDelibereOrThrow(delibereId);
        StatutExpedition ancien = delibere.getStatutExpedition();
        delibere.setStatutExpedition(request.statutExpedition());
        delibere = delibereRepository.save(delibere);

        historiqueActionService.enregistrer(delibere.getDossier().getId(), "Modification du statut d'expédition",
                Map.of("delibereId", delibereId, "ancienStatut", ancien.name(), "nouveauStatut", request.statutExpedition().name()));

        return delibereMapper.toResponse(delibere);
    }

    private Delibere getDelibereOrThrow(Long id) {
        return delibereRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Délibéré introuvable : " + id));
    }

    private Dossier getDossierOrThrow(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + id));
    }
}
