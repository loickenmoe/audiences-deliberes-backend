package com.afriland.audiences.frais.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.SeuilOrigine;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.frais.domain.DecisionValidationFrais;
import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.EtapeValidationFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.domain.StatutPaiementFrais;
import com.afriland.audiences.frais.domain.ValidationFrais;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.frais.repository.ValidationFraisRepository;
import com.afriland.audiences.frais.web.dto.ControleConformiteRequest;
import com.afriland.audiences.frais.web.dto.ControleOpportuniteRequest;
import com.afriland.audiences.frais.web.dto.DemandeFraisResponse;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.portail.web.dto.PieceJustificativeResponse;
import com.afriland.audiences.frais.web.dto.DeposerFraisRequest;
import com.afriland.audiences.frais.web.dto.ValidationConjointeRequest;
import com.afriland.audiences.frais.web.dto.ValidationFraisResponse;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import com.afriland.audiences.common.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Epic 4 (RG-INT-01/03/04/05): full workflow filing → compliance → appropriateness → joint
 * approval → payment.
 */
@Service
@RequiredArgsConstructor
public class FraisService {

    private static final String CLE_DEMANDE_ID = "demandeId";
    private static final String MESSAGE_SUR_LE_DOSSIER = " sur le dossier ";

    private static final String CLE_SEUIL_MONTANT_DEFAUT = "SEUIL_MONTANT_DEFAUT";

    private final DemandeFraisRepository demandeFraisRepository;
    private final ValidationFraisRepository validationFraisRepository;
    private final DossierRepository dossierRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final CompteAvocatService compteAvocatService;
    private final NotificationPrestataireService notificationPrestataireService;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserService currentUserService;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final ConfigurationService configurationService;
    // Pre-signed URLs of the actually stored documents, on the detail view only.
    private final MinioStorageService minioStorageService;

    /**
     * FR-INT-01/RG-INT-01: only a lawyer assigned to the case (table {@code dossier_avocat}) may file
     * a request. Formal appointment by signed letter is a separate process (module {@code intervenant}).
     */
    @Transactional
    public DemandeFraisResponse deposer(DeposerFraisRequest request) {
        Dossier dossier = getDossierOrThrow(request.dossierId());
        Avocat avocat = getAvocatCourantOrThrow();
        if (!dossierAvocatRepository.existsByDossierIdAndAvocatId(dossier.getId(), avocat.getId())) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Seul un avocat affecté à ce dossier peut y déposer une demande de frais (RG-INT-01)");
        }
        if (demandeFraisRepository.existsByReferenceFacture(request.referenceFacture())) {
            throw ApiException.conflictSurChamp(ErrorCodes.FACTURE_EN_DOUBLE,
                    "Une demande de frais porte déjà la référence de facture « " + request.referenceFacture() + " »",
                    "referenceFacture");
        }

        DemandeFrais demande = demandeFraisRepository.save(DemandeFrais.builder()
                .dossier(dossier)
                .avocat(avocat)
                .montant(request.montant())
                .piecesJustificatives(request.piecesJustificatives())
                .referenceFacture(request.referenceFacture())
                .statutCircuit(StatutCircuitFrais.DEPOSEE)
                .statutPaiement(StatutPaiementFrais.EN_ATTENTE)
                .build());

        historiqueActionService.enregistrer(dossier.getId(), TypeActionHistorique.FRAIS_DEMANDE_DEPOSEE,
                Map.of(CLE_DEMANDE_ID, demande.getId(), "montant", request.montant()));

        notifierProfil("ASSISTANTE", dossier.getId(), TypeAlerte.NOUVELLE_DEMANDE_FRAIS,
                "Nouvelle demande de frais déposée sur le dossier " + dossier.getReference()
                        + " (RG-INT-01), en attente de contrôle de conformité.");

        return versReponse(demande);
    }

    /** FR-INT-02/RG-INT-03/05: compliance check by the assistant. */
    @Transactional
    public DemandeFraisResponse controlerConformite(Long demandeId, ControleConformiteRequest request) {
        DemandeFrais demande = getDemandeOrThrow(demandeId);
        exigerStatut(demande, StatutCircuitFrais.DEPOSEE);
        Utilisateur assistante = currentUserService.getCurrentUser();

        if (Boolean.TRUE.equals(request.conforme())) {
            demande.setStatutCircuit(StatutCircuitFrais.CONFORMITE);
            demandeFraisRepository.save(demande);
            enregistrerValidation(demande, EtapeValidationFrais.CONFORMITE, assistante, DecisionValidationFrais.ACCORD, null);
            notifierProfil("DJA", demande.getDossier().getId(), TypeAlerte.NOUVELLE_DEMANDE_FRAIS,
                    "La demande de frais " + demandeId + " est conforme (RG-INT-03), en attente de contrôle d'opportunité.");
        } else {
            exigerMotif(request.motifRejet());
            rejeter(demande, assistante, EtapeValidationFrais.CONFORMITE, request.motifRejet());
        }

        historiqueActionService.enregistrer(demande.getDossier().getId(), TypeActionHistorique.FRAIS_CONFORMITE_CONTROLEE,
                Map.of(CLE_DEMANDE_ID, demandeId, "conforme", request.conforme()));

        return versReponse(demande);
    }

    /**
     * FR-INT-03/RG-INT-03: the DJA assesses appropriateness; {@code estSensible = true} or
     * {@code montant > seuilApplicable} triggers the DJ+DJA joint approval, otherwise the DJA
     * approves alone.
     */
    @Transactional
    public DemandeFraisResponse controlerOpportunite(Long demandeId, ControleOpportuniteRequest request) {
        DemandeFrais demande = getDemandeOrThrow(demandeId);
        exigerStatut(demande, StatutCircuitFrais.CONFORMITE);
        Utilisateur dja = currentUserService.getCurrentUser();

        if (Boolean.TRUE.equals(request.accord())) {
            enregistrerValidation(demande, EtapeValidationFrais.OPPORTUNITE, dja, DecisionValidationFrais.ACCORD, null);
            Dossier dossier = demande.getDossier();
            if (exigeValidationConjointe(dossier, demande.getMontant(), seuilApplicable(dossier, seuilGlobal()))) {
                demande.setStatutCircuit(StatutCircuitFrais.OPPORTUNITE);
                demandeFraisRepository.save(demande);
                signalerValidationConjointeRequise(demande);
            } else {
                demande.setStatutCircuit(StatutCircuitFrais.VALIDEE);
                demandeFraisRepository.save(demande);
            }
        } else {
            exigerMotif(request.motifRejet());
            rejeter(demande, dja, EtapeValidationFrais.OPPORTUNITE, request.motifRejet());
        }

        historiqueActionService.enregistrer(demande.getDossier().getId(), TypeActionHistorique.FRAIS_OPPORTUNITE_CONTROLEE,
                Map.of(CLE_DEMANDE_ID, demandeId, "accord", request.accord()));

        return versReponse(demande);
    }

    /**
     * FR-INT-04/RG-INT-03: the DJ and the DJA each record their own approval; both are required for
     * {@code VALIDEE}, a single refusal is enough for {@code REJETEE}.
     */
    @Transactional
    public DemandeFraisResponse validerConjointement(Long demandeId, ValidationConjointeRequest request) {
        DemandeFrais demande = getDemandeOrThrow(demandeId);
        exigerStatut(demande, StatutCircuitFrais.OPPORTUNITE);
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        EtapeValidationFrais etape = etapeConjointe(utilisateur);

        if (validationFraisRepository.findByDemandeFraisIdAndEtape(demandeId, etape).isPresent()) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Votre accord a déjà été enregistré pour cette demande");
        }

        if (Boolean.TRUE.equals(request.accord())) {
            enregistrerValidation(demande, etape, utilisateur, DecisionValidationFrais.ACCORD, null);
            boolean autreAccordObtenu = validationFraisRepository.existsByDemandeFraisIdAndEtapeAndDecision(
                    demandeId, etape == EtapeValidationFrais.CONJOINTE_DJ ? EtapeValidationFrais.CONJOINTE_DJA : EtapeValidationFrais.CONJOINTE_DJ,
                    DecisionValidationFrais.ACCORD);
            if (autreAccordObtenu) {
                demande.setStatutCircuit(StatutCircuitFrais.VALIDEE);
                demandeFraisRepository.save(demande);
            }
        } else {
            exigerMotif(request.motifRejet());
            rejeter(demande, utilisateur, etape, request.motifRejet());
        }

        historiqueActionService.enregistrer(demande.getDossier().getId(), TypeActionHistorique.FRAIS_VALIDATION_CONJOINTE,
                Map.of(CLE_DEMANDE_ID, demandeId, "etape", etape.name(), "accord", request.accord()));

        return versReponse(demande);
    }

    /** FR-INT-05/RG-INT-04: payment by the assistant; the request must be {@code VALIDEE}. */
    @Transactional
    public DemandeFraisResponse enregistrerPaiement(Long demandeId) {
        DemandeFrais demande = getDemandeOrThrow(demandeId);
        if (demande.getStatutCircuit() != StatutCircuitFrais.VALIDEE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Seule une demande VALIDEE peut être marquée payée (RG-INT-04)");
        }

        demande.setStatutCircuit(StatutCircuitFrais.PAYEE);
        demande.setStatutPaiement(StatutPaiementFrais.PAYEE);
        DemandeFrais payee = demandeFraisRepository.save(demande);
        Long dossierId = payee.getDossier().getId();

        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.FRAIS_PAIEMENT_ENREGISTRE, Map.of(CLE_DEMANDE_ID, demandeId));

        String message = "Le paiement de votre demande de frais " + demandeId + MESSAGE_SUR_LE_DOSSIER
                + payee.getDossier().getReference() + " a été effectué (RG-INT-04).";
        compteAvocatService.utilisateurDe(payee.getAvocat())
                .ifPresent(u -> alerteService.declencher(TypeAlerte.PAIEMENT_EFFECTUE, dossierId, u, message));
        // External channels: neither the amount nor the request reference is included.
        notificationPrestataireService.notifier(EvenementPrestataire.PAIEMENT_EFFECTUE, payee.getAvocat(),
                dossierId, "frais-" + demandeId + "-paiement");
        notifierProfil("DJ", dossierId, TypeAlerte.PAIEMENT_EFFECTUE, message);
        notifierProfil("DJA", dossierId, TypeAlerte.PAIEMENT_EFFECTUE, message);

        return versReponse(payee);
    }

    /** Work queue filterable by status: restricted to the lawyer's own requests for ROLE_AVOCAT, cross-cutting for internal staff. */
    @Transactional(readOnly = true)
    public PageResponse<DemandeFraisResponse> lister(StatutCircuitFrais statut, Long dossierId, Long avocatId,
                                                       String referenceFacture, LocalDate dateDebut, LocalDate dateFin,
                                                       Pageable pageable) {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        /*
         * For a lawyer, the id is forced, never taken from the request: otherwise changing one digit in
         * the URL would expose a colleague's requests. The search filter and the access restriction
         * cannot be the same parameter.
         */
        Long avocatFiltre = avocatId;
        if ("AVOCAT".equals(utilisateur.getProfil().getCode())) {
            Avocat avocat = compteAvocatService.avocatDe(utilisateur)
                    .orElseThrow(() -> ApiException.forbidden(ErrorCodes.AVOCAT_NON_RATTACHE,
                        "Aucun avocat du référentiel n'est rattaché à ce compte"));
            avocatFiltre = avocat.getId();
        }
        Page<DemandeFrais> page = demandeFraisRepository.findAll(
                DemandeFraisSpecifications.withFilters(avocatFiltre, statut, dossierId, referenceFacture,
                        dateDebut, dateFin),
                pageable);
        // One query for the decisions of the whole page, and a single global threshold.
        Map<Long, List<ValidationFrais>> validations = validationFraisRepository
                .findByDemandeFraisIdInOrderByDateValidationAscIdAsc(page.map(DemandeFrais::getId).getContent())
                .stream()
                .collect(Collectors.groupingBy(v -> v.getDemandeFrais().getId()));
        double seuilGlobal = page.isEmpty() ? 0 : seuilGlobal();
        // No pre-signed URLs on the list: they only live 10 minutes and would cost one MinIO round trip
        // per document, for a screen that does not display them.
        return PageResponse.of(page,
                d -> versReponse(d, validations.getOrDefault(d.getId(), List.of()), seuilGlobal, List.of()));
    }

    /**
     * Detail of a request with its workflow decisions (UC-INT-02 step 3: the request, its supporting
     * documents and the compliance check result). A lawyer can only read their own requests.
     */
    @Transactional(readOnly = true)
    public DemandeFraisResponse consulter(Long demandeId) {
        DemandeFrais demande = getDemandeOrThrow(demandeId);
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        if ("AVOCAT".equals(utilisateur.getProfil().getCode())
                && !compteAvocatService.estLeCompteDe(utilisateur, demande.getAvocat())) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS, "Cette demande de frais n'est pas la vôtre");
        }
        return versReponse(demande,
                validationFraisRepository.findByDemandeFraisIdOrderByDateValidationAscIdAsc(demande.getId()),
                seuilGlobal(), piecesTelechargeables(demande));
    }

    /**
     * Terminal rejection of a request at any step (RG-INT-03): the lawyer is notified each time. Only
     * FR-INT-02 says so explicitly, but RG-INT-03 is the same rule for FR-INT-02/03/04.
     */
    private void rejeter(DemandeFrais demande, Utilisateur decideur, EtapeValidationFrais etape, String motif) {
        enregistrerValidation(demande, etape, decideur, DecisionValidationFrais.REJET, motif);
        demande.setStatutCircuit(StatutCircuitFrais.REJETEE);
        demande.setMotifRejet(motif);
        demandeFraisRepository.save(demande);

        String message = "Votre demande de frais " + demande.getId() + MESSAGE_SUR_LE_DOSSIER
                + demande.getDossier().getReference() + " a été rejetée : " + motif;
        compteAvocatService.utilisateurDe(demande.getAvocat())
                .ifPresent(u -> alerteService.declencher(TypeAlerte.NOUVELLE_DEMANDE_FRAIS, demande.getDossier().getId(), u, message));
        // External channels: the rejection reason stays in the application.
        notificationPrestataireService.notifier(EvenementPrestataire.FRAIS_REJETES, demande.getAvocat(),
                demande.getDossier().getId(), "frais-" + demande.getId() + "-rejet");
    }

    private void enregistrerValidation(DemandeFrais demande, EtapeValidationFrais etape, Utilisateur utilisateur,
                                        DecisionValidationFrais decision, String motif) {
        validationFraisRepository.save(ValidationFrais.builder()
                .demandeFrais(demande)
                .etape(etape)
                .utilisateur(utilisateur)
                .decision(decision)
                .motif(motif)
                .build());
    }

    /** RG-ALR: tells the DJ and the DJA that a joint approval is required (FR-INT-03, sensitive case or amount &gt; threshold). */
    private void signalerValidationConjointeRequise(DemandeFrais demande) {
        String message = "La demande de frais " + demande.getId() + MESSAGE_SUR_LE_DOSSIER
                + demande.getDossier().getReference() + " requiert une validation conjointe DJ+DJA (RG-INT-03).";
        notifierProfil("DJ", demande.getDossier().getId(), TypeAlerte.VALIDATION_CONJOINTE_REQUISE, message);
        notifierProfil("DJA", demande.getDossier().getId(), TypeAlerte.VALIDATION_CONJOINTE_REQUISE, message);
    }

    private void notifierProfil(String profilCode, Long dossierId, TypeAlerte type, String declencheur) {
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue(profilCode);
        destinataires.forEach(destinataire -> alerteService.declencher(type, dossierId, destinataire, declencheur));
    }

    /** Case threshold if an approved exemption exists, otherwise the global threshold. */
    private static double seuilApplicable(Dossier dossier, double seuilGlobal) {
        if (dossier.getSeuilOrigine() == SeuilOrigine.DEROGATION && dossier.getSeuilMontant() != null) {
            return dossier.getSeuilMontant();
        }
        return seuilGlobal;
    }

    private double seuilGlobal() {
        return Double.parseDouble(configurationService.getOrThrow(CLE_SEUIL_MONTANT_DEFAUT).getValeur());
    }

    /** RG-INT-03: {@code estSensible = true} OR {@code montant > seuilApplicable}. */
    private static boolean exigeValidationConjointe(Dossier dossier, Double montant, double seuil) {
        return Boolean.TRUE.equals(dossier.getEstSensible()) || (montant != null && montant > seuil);
    }

    private DemandeFraisResponse versReponse(DemandeFrais demande) {
        return versReponse(demande,
                validationFraisRepository.findByDemandeFraisIdOrderByDateValidationAscIdAsc(demande.getId()),
                seuilGlobal(), List.of());
    }

    /**
     * UC-INT-02 step 3: the reviewer must <b>see</b> the documents, not just their names. Only entries
     * uploaded through the portal are signed: the array may hold labels predating upload, which
     * point to no stored object and for which a pre-signed URL would fail.
     */
    private List<PieceJustificativeResponse> piecesTelechargeables(DemandeFrais demande) {
        List<String> pieces = demande.getPiecesJustificatives();
        if (pieces == null) {
            return List.of();
        }
        return pieces.stream()
                .filter(cle -> cle != null && cle.startsWith(MinioStorageService.PREFIXE_PIECES_FRAIS + "/"))
                .map(cle -> new PieceJustificativeResponse(cle, nomDOrigine(cle),
                        minioStorageService.urlTelechargement(cle)))
                .toList();
    }

    /** The key is {@code frais/{uuid}-{name}}: only the original name is human-readable. */
    private static String nomDOrigine(String cle) {
        String fin = cle.substring(cle.lastIndexOf('/') + 1);
        int tiret = fin.indexOf('-', 35);
        return tiret >= 0 ? fin.substring(tiret + 1) : fin;
    }

    /**
     * {@code validationConjointeRequise} applies the current rule (RG-INT-03): for a request that has
     * not reached the appropriateness step yet, it is what awaits it.
     */
    private static DemandeFraisResponse versReponse(DemandeFrais demande, List<ValidationFrais> validations,
                                                      double seuilGlobal, List<PieceJustificativeResponse> pieces) {
        Dossier dossier = demande.getDossier();
        double seuil = seuilApplicable(dossier, seuilGlobal);
        return new DemandeFraisResponse(
                demande.getId(),
                dossier.getId(),
                dossier.getReference(),
                demande.getAvocat().getId(),
                demande.getAvocat().getNom(),
                demande.getMontant(),
                demande.getPiecesJustificatives(),
                demande.getStatutCircuit(),
                demande.getMotifRejet(),
                demande.getStatutPaiement(),
                demande.getReferenceFacture(),
                dossier.getEstSensible(),
                seuil,
                exigeValidationConjointe(dossier, demande.getMontant(), seuil),
                validations.stream().map(FraisService::versReponse).toList(),
                pieces,
                demande.getCreatedAt(),
                demande.getUpdatedAt());
    }

    private static ValidationFraisResponse versReponse(ValidationFrais validation) {
        Utilisateur auteur = validation.getUtilisateur();
        return new ValidationFraisResponse(
                validation.getEtape(),
                validation.getDecision(),
                validation.getMotif(),
                auteur != null ? auteur.getId() : null,
                auteur == null ? null : Stream.of(auteur.getPrenom(), auteur.getNom())
                        .filter(Objects::nonNull).collect(Collectors.joining(" ")),
                validation.getDateValidation());
    }

    private EtapeValidationFrais etapeConjointe(Utilisateur utilisateur) {
        String code = utilisateur.getProfil().getCode();
        if ("DJ".equals(code)) {
            return EtapeValidationFrais.CONJOINTE_DJ;
        }
        if ("DJA".equals(code)) {
            return EtapeValidationFrais.CONJOINTE_DJA;
        }
        throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS, "Seuls le DJ et le DJA valident conjointement une demande de frais");
    }

    private Avocat getAvocatCourantOrThrow() {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        return compteAvocatService.avocatDe(utilisateur)
                .orElseThrow(() -> ApiException.forbidden(ErrorCodes.AVOCAT_NON_RATTACHE,
                        "Aucun avocat du référentiel n'est rattaché à ce compte"));
    }

    private void exigerStatut(DemandeFrais demande, StatutCircuitFrais attendu) {
        if (demande.getStatutCircuit() != attendu) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "Cette demande doit être au statut " + attendu + " (statut actuel : " + demande.getStatutCircuit() + ")");
        }
    }

    private void exigerMotif(String motif) {
        if (!StringUtils.hasText(motif)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Le motif de rejet est obligatoire");
        }
    }

    private DemandeFrais getDemandeOrThrow(Long id) {
        return demandeFraisRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Demande de frais introuvable : " + id));
    }

    private Dossier getDossierOrThrow(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + id));
    }
}
