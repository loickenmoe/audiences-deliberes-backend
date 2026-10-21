package com.afriland.audiences.delibere.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.delibere.domain.Delibere;
import com.afriland.audiences.delibere.domain.EtatDelibere;
import com.afriland.audiences.delibere.domain.Prorogation;
import com.afriland.audiences.delibere.domain.ResultatDelibere;
import com.afriland.audiences.delibere.domain.StatutExpedition;
import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.delibere.repository.ProrogationRepository;
import com.afriland.audiences.delibere.web.dto.CreerProrogationRequest;
import com.afriland.audiences.delibere.web.dto.DelibereResponse;
import com.afriland.audiences.delibere.web.dto.EnregistrerDelibereRequest;
import com.afriland.audiences.delibere.web.dto.ExpeditionRequest;
import com.afriland.audiences.delibere.web.dto.ProrogationResponse;
import com.afriland.audiences.delibere.web.dto.RabattementRequest;
import com.afriland.audiences.delibere.web.dto.RecoursResponse;
import com.afriland.audiences.delibere.web.dto.ViderDelibereRequest;
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

/**
 * Epic 3 (RG-DEL-01/02/03/04/06/07).
 * <p>
 * Deliberation lifecycle (US 3.2/3.4, UC-DEL-04/05, RG-DEL-06/07): placed under deliberation (no
 * result, step {@code EN_DELIBERE}), then extensions and reopening <b>while</b> pending, then the
 * result, which delivers the deliberation (step {@code DELIBERE_VIDE}). An already-rendered
 * deliberation can still be recorded in one go, result included (FR-DEL-01).
 */
@Service
@RequiredArgsConstructor
public class DelibereService {

    private static final String CLE_DELIBERE_ID = "delibereId";

    private final DelibereRepository delibereRepository;
    private final ProrogationRepository prorogationRepository;
    private final DossierRepository dossierRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;
    private final EtapeProcedureMapper etapeProcedureMapper;
    private final UtilisateurRepository utilisateurRepository;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final ConfigurationService configurationService;

    /**
     * Without a result: placed under deliberation, the step stays {@code EN_DELIBERE}. With a result:
     * an already-rendered deliberation, recorded and delivered in one go (FR-DEL-01).
     */
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
        if (delibereRepository.existsByEtapeIdAndResultatIsNullAndDateRabattementIsNull(etape.getId())) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "Un délibéré est déjà en attente sur cette étape : prorogez-le, rabattez-le ou videz-le (Q-78)");
        }
        // Validate everything before writing: a deliberation is never created and then rejected.
        if (request.resultat() != null) {
            validerEcheanceRecours(request.resultat(), request.dateDeliberee(), request.dateEcheanceRecours());
        } else if (request.dateEcheanceRecours() != null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "L'échéance du délai de recours se saisit avec le résultat du délibéré");
        }

        Delibere delibere = delibereRepository.save(Delibere.builder()
                .dossier(dossier)
                .etape(etape)
                .dateDeliberee(request.dateDeliberee())
                .build());

        if (request.resultat() == null) {
            historiqueActionService.enregistrer(dossierId, TypeActionHistorique.DELIBERE_MISE_EN_DELIBERE,
                    Map.of(CLE_DELIBERE_ID, delibere.getId(), "dateDeliberee", request.dateDeliberee().toString()));
            return versReponse(delibere);
        }

        appliquerResultat(delibere, request.resultat(), request.dateEcheanceRecours());
        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.DELIBERE_ENREGISTRE,
                Map.of(CLE_DELIBERE_ID, delibere.getId(), "resultat", request.resultat().name()));
        return versReponse(delibere);
    }

    /** The court has ruled: the pending deliberation is delivered. */
    @Transactional
    public DelibereResponse viderDelibere(Long delibereId, ViderDelibereRequest request) {
        Delibere delibere = getDelibereOrThrow(delibereId);
        exigerEnAttente(delibere, "vidé");
        validerEcheanceRecours(request.resultat(), delibere.getDateDeliberee(), request.dateEcheanceRecours());

        appliquerResultat(delibere, request.resultat(), request.dateEcheanceRecours());
        historiqueActionService.enregistrer(delibere.getDossier().getId(), TypeActionHistorique.DELIBERE_VIDE,
                Map.of(CLE_DELIBERE_ID, delibereId, "resultat", request.resultat().name()));
        return versReponse(delibere);
    }

    /** RG-DEL-01: sets the (already validated) result, opens the enforceable copy and moves the step to "deliberation delivered". */
    private void appliquerResultat(Delibere delibere, ResultatDelibere resultat, LocalDateTime dateEcheanceRecours) {
        delibere.setResultat(resultat);
        delibere.setDateEcheanceRecours(dateEcheanceRecours);
        delibere.setStatutExpedition(StatutExpedition.A_LEVER);
        delibereRepository.save(delibere);

        EtapeProcedure etape = delibere.getEtape();
        etape.setStatutCycleVie(StatutCycleVie.DELIBERE_VIDE);
        etapeProcedureRepository.save(etape);
    }

    /** RG-DEL-01: only DEFAVORABLE/MIXTE open an appeal; a deadline later than the deliberation is then required, otherwise it must be absent. */
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
     * Extension, reopening and delivery only apply to a <b>pending</b> deliberation on an
     * {@code EN_DELIBERE} step: the court extends or reopens during deliberation (US 3.2/3.4, UC-DEL-04/05).
     */
    private void exigerEnAttente(Delibere delibere, String action) {
        if (delibere.getDateRabattement() != null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Ce délibéré a été rabattu : il ne peut plus être " + action + " (RG-DEL-07)");
        }
        if (delibere.getResultat() != null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Ce délibéré est déjà vidé : il ne peut plus être " + action + " (Q-78)");
        }
        EtapeProcedure etape = delibere.getEtape();
        if (etape == null || etape.getStatutCycleVie() != StatutCycleVie.EN_DELIBERE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Un délibéré ne peut être " + action + " que tant que l'étape est EN_DELIBERE (Q-78)");
        }
    }

    /**
     * RG-DEL-06: unlimited in number, never overwritten. The new date, which must be in the future
     * (US 3.2), becomes the announced deliberation date; the step stays {@code EN_DELIBERE}.
     */
    @Transactional
    public ProrogationResponse proroger(Long delibereId, CreerProrogationRequest request) {
        Delibere delibere = getDelibereOrThrow(delibereId);
        exigerEnAttente(delibere, "prorogé");
        if (!request.date().isAfter(LocalDate.now())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La nouvelle date du délibéré doit être future (US 3.2)");
        }

        int compteur = (int) prorogationRepository.countByDelibereId(delibereId) + 1;
        Prorogation prorogation = prorogationRepository.save(Prorogation.builder()
                .delibere(delibere)
                .date(request.date())
                .motif(request.motif())
                .compteur(compteur)
                .build());

        delibere.setDateDeliberee(request.date());
        delibereRepository.save(delibere);

        historiqueActionService.enregistrer(delibere.getDossier().getId(), TypeActionHistorique.DELIBERE_PROROGE,
                Map.of(CLE_DELIBERE_ID, delibereId, "compteur", compteur, "date", request.date().toString()));

        boolean alerte = compteur > seuilProrogations();
        if (alerte) {
            signalerProrogation(delibere, compteur);
        }

        return new ProrogationResponse(prorogation.getId(), delibereId, prorogation.getDate(), prorogation.getMotif(),
                compteur, alerte, prorogation.getCreatedAt());
    }

    /** FR-DEL-04 criterion 2: extension history, in the order the extensions were pronounced. */
    @Transactional(readOnly = true)
    public List<ProrogationResponse> listerProrogations(Long delibereId) {
        getDelibereOrThrow(delibereId);
        int seuil = seuilProrogations();
        return prorogationRepository.findByDelibereIdOrderByCompteurAsc(delibereId).stream()
                .map(p -> new ProrogationResponse(p.getId(), delibereId, p.getDate(), p.getMotif(),
                        p.getCompteur(), p.getCompteur() > seuil, p.getCreatedAt()))
                .toList();
    }

    private int seuilProrogations() {
        return Integer.parseInt(configurationService.getOrThrow("SEUIL_PROROGATIONS").getValeur());
    }

    /** RG-DEL-06/RG-ALR-01: crossing the threshold is reported once only, to the DJ and the DJA. */
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
     * RG-DEL-07/FR-DEL-05: the judge reopens the proceedings before ruling. The pending deliberation
     * is marked reopened and the step returns to {@code EN_COURS}, so new hearings can be scheduled.
     * No next step is opened, which is what distinguishes it from lodging an appeal.
     */
    @Transactional
    public EtapeProcedureResponse rabattre(Long delibereId, RabattementRequest request) {
        Delibere delibere = getDelibereOrThrow(delibereId);
        exigerEnAttente(delibere, "rabattu");

        String motif = request.motif().trim();
        delibere.setDateRabattement(request.date());
        delibere.setMotifRabattement(motif);
        delibereRepository.save(delibere);

        EtapeProcedure etape = delibere.getEtape();
        etape.setStatutCycleVie(StatutCycleVie.EN_COURS);
        etape = etapeProcedureRepository.save(etape);

        historiqueActionService.enregistrer(delibere.getDossier().getId(), TypeActionHistorique.DELIBERE_RABATTU,
                Map.of(CLE_DELIBERE_ID, delibereId, "date", request.date().toString(), "motif", motif));

        return etapeProcedureMapper.toResponse(etape);
    }

    /** FR-DEL-02: read-only, this GET never creates an alert. */
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
     * An appeal was lodged on this step if the next step (RECOURS_1/2) was opened automatically,
     * unlike a reopening, which replays the same step without opening the next one
     * (see {@code EtapeTransitionValidator#estExerciceDeRecours}).
     */
    private boolean recoursAEteExerce(Long dossierId, EtapeProcedure etape) {
        if (etape == null || etape.getType().estTerminale()) {
            return false;
        }
        return etapeProcedureRepository.existsByDossierIdAndType(dossierId, etape.getType().suivante());
    }

    /** RG-DEL-03: the enforceable copy can only be collected once the decision is rendered. */
    @Transactional
    public DelibereResponse modifierExpedition(Long delibereId, ExpeditionRequest request) {
        Delibere delibere = getDelibereOrThrow(delibereId);
        if (delibere.getResultat() == null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "L'expédition ne se lève qu'une fois le délibéré vidé (Q-78)");
        }
        StatutExpedition ancien = delibere.getStatutExpedition();
        delibere.setStatutExpedition(request.statutExpedition());
        delibere = delibereRepository.save(delibere);

        historiqueActionService.enregistrer(delibere.getDossier().getId(), TypeActionHistorique.DELIBERE_EXPEDITION_MODIFIEE,
                Map.of(CLE_DELIBERE_ID, delibereId, "ancienStatut", ancien.name(), "nouveauStatut", request.statutExpedition().name()));

        return versReponse(delibere);
    }

    private Delibere getDelibereOrThrow(Long id) {
        return delibereRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Délibéré introuvable : " + id));
    }

    /** Case file "Deliberations" tab; 404 if the case does not exist. */
    @Transactional(readOnly = true)
    public List<DelibereResponse> listerParDossier(Long dossierId) {
        getDossierOrThrow(dossierId);
        return delibereRepository.findByDossierIdOrderByDateDelibereeDesc(dossierId).stream()
                .map(this::versReponse)
                .toList();
    }

    /** The state is derived, never stored, so it cannot diverge from the data it is based on. */
    private static EtatDelibere etatDe(Delibere delibere) {
        if (delibere.getDateRabattement() != null) {
            return EtatDelibere.RABATTU;
        }
        return delibere.getResultat() != null ? EtatDelibere.VIDE : EtatDelibere.EN_ATTENTE;
    }

    private DelibereResponse versReponse(Delibere delibere) {
        EtatDelibere etat = etatDe(delibere);
        return new DelibereResponse(
                delibere.getId(),
                delibere.getDossier() != null ? delibere.getDossier().getId() : null,
                delibere.getEtape() != null ? delibere.getEtape().getId() : null,
                delibere.getDateDeliberee(),
                etat,
                delibere.getResultat(),
                delibere.getStatutExpedition(),
                delibere.getDateEcheanceRecours(),
                delibere.getId() != null ? prorogationRepository.countByDelibereId(delibere.getId()) : 0L,
                delibere.getDateRabattement(),
                delibere.getMotifRabattement(),
                delibere.getCreatedAt());
    }

    private Dossier getDossierOrThrow(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + id));
    }
}
