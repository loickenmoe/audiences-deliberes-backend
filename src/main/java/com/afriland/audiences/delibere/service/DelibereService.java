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
 * Épic 3 (RG-DEL-01/02/03/04/06/07) — cf. FUNCTIONAL_MAP.md §3, API_IMPLEMENTATION_STATUS.md §4.
 * <p>
 * <b>Cycle d'un délibéré (Q-78)</b>, conforme aux sources (US 3.2/3.4, UC-DEL-04/05, RG-DEL-06/07) :
 * mise en délibéré (sans résultat, étape {@code EN_DELIBERE}) → prorogations et rabattement
 * <b>pendant</b> cette attente → résultat, qui vide le délibéré (étape {@code DELIBERE_VIDE}).
 * Un délibéré déjà rendu peut encore s'enregistrer d'un seul geste, résultat compris (FR-DEL-01).
 */
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

    /**
     * Sans résultat : mise en délibéré, l'étape reste {@code EN_DELIBERE} (Q-78). Avec résultat :
     * délibéré déjà rendu, enregistré et vidé d'un même geste — comportement historique (FR-DEL-01).
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
        // Tout se valide avant d'écrire : un délibéré n'est jamais créé puis refusé.
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
                    Map.of("delibereId", delibere.getId(), "dateDeliberee", request.dateDeliberee().toString()));
            return versReponse(delibere);
        }

        appliquerResultat(delibere, request.resultat(), request.dateEcheanceRecours());
        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.DELIBERE_ENREGISTRE,
                Map.of("delibereId", delibere.getId(), "resultat", request.resultat().name()));
        return versReponse(delibere);
    }

    /** Q-78 : le tribunal a rendu sa décision — le délibéré en attente est vidé. */
    @Transactional
    public DelibereResponse viderDelibere(Long delibereId, ViderDelibereRequest request) {
        Delibere delibere = getDelibereOrThrow(delibereId);
        exigerEnAttente(delibere, "vidé");
        validerEcheanceRecours(request.resultat(), delibere.getDateDeliberee(), request.dateEcheanceRecours());

        appliquerResultat(delibere, request.resultat(), request.dateEcheanceRecours());
        historiqueActionService.enregistrer(delibere.getDossier().getId(), TypeActionHistorique.DELIBERE_VIDE,
                Map.of("delibereId", delibereId, "resultat", request.resultat().name()));
        return versReponse(delibere);
    }

    /** RG-DEL-01 : pose le résultat (déjà validé), ouvre l'expédition et fait passer l'étape « Délibéré vidé ». */
    private void appliquerResultat(Delibere delibere, ResultatDelibere resultat, LocalDateTime dateEcheanceRecours) {
        delibere.setResultat(resultat);
        delibere.setDateEcheanceRecours(dateEcheanceRecours);
        delibere.setStatutExpedition(StatutExpedition.A_LEVER);
        delibereRepository.save(delibere);

        EtapeProcedure etape = delibere.getEtape();
        etape.setStatutCycleVie(StatutCycleVie.DELIBERE_VIDE);
        etapeProcedureRepository.save(etape);
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
     * Q-78 : prorogation, rabattement et vidage ne portent que sur un délibéré <b>en attente</b>,
     * étape {@code EN_DELIBERE} — c'est pendant la délibération que le tribunal proroge ou rabat
     * (US 3.2/3.4, UC-DEL-04/05). Remplace la lecture de Q-44, qui les plaçait après la décision.
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
     * RG-DEL-06 : « DÉLIBÉRÉ PROROGÉ AU [date] » — nombre illimité, jamais écrasé. La nouvelle date,
     * future (US 3.2), devient la date annoncée du délibéré ; l'étape reste {@code EN_DELIBERE}.
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
                Map.of("delibereId", delibereId, "compteur", compteur, "date", request.date().toString()));

        boolean alerte = compteur > seuilProrogations();
        if (alerte) {
            signalerProrogation(delibere, compteur);
        }

        return new ProrogationResponse(prorogation.getId(), delibereId, prorogation.getDate(), prorogation.getMotif(),
                compteur, alerte, prorogation.getCreatedAt());
    }

    /** Q-78, FR-DEL-04 critère 2 : historique des prorogations, dans l'ordre où elles ont été prononcées. */
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
     * RG-DEL-07/FR-DEL-05 : « DÉLIBÉRÉ RABATTU LE [date, motif] » — le juge rouvre les débats avant
     * de statuer. Le délibéré en attente est marqué rabattu et l'étape revient {@code EN_COURS} :
     * de nouvelles audiences sont planifiables, et l'étape pourra repasser en délibéré. Aucune étape
     * suivante n'est ouverte — c'est ce qui le distingue de l'exercice d'un recours (Q-42).
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
                Map.of("delibereId", delibereId, "date", request.date().toString(), "motif", motif));

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

    /** RG-DEL-03 : la grosse ne se lève qu'une fois la décision rendue (Q-78). */
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
                Map.of("delibereId", delibereId, "ancienStatut", ancien.name(), "nouveauStatut", request.statutExpedition().name()));

        return versReponse(delibere);
    }

    private Delibere getDelibereOrThrow(Long id) {
        return delibereRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Délibéré introuvable : " + id));
    }

    /** M15 (Q-68) : onglet « Délibérés » de la fiche dossier — 404 si le dossier n'existe pas. */
    @Transactional(readOnly = true)
    public List<DelibereResponse> listerParDossier(Long dossierId) {
        getDossierOrThrow(dossierId);
        return delibereRepository.findByDossierIdOrderByDateDelibereeDesc(dossierId).stream()
                .map(this::versReponse)
                .toList();
    }

    /** L'état est dérivé, jamais stocké : il ne peut pas diverger des données qui le fondent. */
    private DelibereResponse versReponse(Delibere delibere) {
        EtatDelibere etat = delibere.getDateRabattement() != null ? EtatDelibere.RABATTU
                : delibere.getResultat() != null ? EtatDelibere.VIDE
                : EtatDelibere.EN_ATTENTE;
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
