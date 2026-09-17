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
 * Épic 4 (RG-INT-01/03/04/05) — circuit complet dépôt → conformité → opportunité → validation
 * conjointe → paiement. Cf. FUNCTIONAL_MAP.md §4, API_IMPLEMENTATION_STATUS.md §5.
 */
@Service
@RequiredArgsConstructor
public class FraisService {

    private static final String CLE_SEUIL_MONTANT_DEFAUT = "SEUIL_MONTANT_DEFAUT";

    private final DemandeFraisRepository demandeFraisRepository;
    private final ValidationFraisRepository validationFraisRepository;
    private final DossierRepository dossierRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final CompteAvocatService compteAvocatService;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserService currentUserService;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final ConfigurationService configurationService;
    // Q-97 : URL pré-signées des pièces réellement stockées, sur le détail seulement.
    private final MinioStorageService minioStorageService;

    /**
     * FR-INT-01/RG-INT-01 : seul un avocat affecté au dossier (table {@code dossier_avocat},
     * même relation que celle retenue en Q-28 — la constitution formelle par lettre signée du SH
     * est un processus distinct, cf. module {@code intervenant}) peut y déposer une demande.
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
                Map.of("demandeId", demande.getId(), "montant", request.montant()));

        notifierProfil("ASSISTANTE", dossier.getId(), TypeAlerte.NOUVELLE_DEMANDE_FRAIS,
                "Nouvelle demande de frais déposée sur le dossier " + dossier.getReference()
                        + " (RG-INT-01), en attente de contrôle de conformité.");

        return versReponse(demande);
    }

    /** FR-INT-02/RG-INT-03/05 : contrôle de conformité par l'Assistante. */
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
                Map.of("demandeId", demandeId, "conforme", request.conforme()));

        return versReponse(demande);
    }

    /**
     * FR-INT-03/RG-INT-03 : le DJA évalue l'opportunité ; {@code estSensible = true} ou
     * {@code montant > seuilApplicable} déclenche la validation conjointe DJ+DJA, sinon le DJA
     * valide seul.
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
                Map.of("demandeId", demandeId, "accord", request.accord()));

        return versReponse(demande);
    }

    /**
     * FR-INT-04/RG-INT-03 : le DJ et le DJA enregistrent chacun leur accord individuel ; les deux
     * accords sont requis pour {@code VALIDEE}, un seul refus suffit pour {@code REJETEE}.
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
                Map.of("demandeId", demandeId, "etape", etape.name(), "accord", request.accord()));

        return versReponse(demande);
    }

    /** FR-INT-05/RG-INT-04 : paiement par l'Assistante, demande nécessairement {@code VALIDEE}. */
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

        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.FRAIS_PAIEMENT_ENREGISTRE, Map.of("demandeId", demandeId));

        String message = "Le paiement de votre demande de frais " + demandeId + " sur le dossier "
                + payee.getDossier().getReference() + " a été effectué (RG-INT-04).";
        compteAvocatService.utilisateurDe(payee.getAvocat())
                .ifPresent(u -> alerteService.declencher(TypeAlerte.PAIEMENT_EFFECTUE, dossierId, u, message));
        notifierProfil("DJ", dossierId, TypeAlerte.PAIEMENT_EFFECTUE, message);
        notifierProfil("DJA", dossierId, TypeAlerte.PAIEMENT_EFFECTUE, message);

        return versReponse(payee);
    }

    /** Endpoint 🆕 (Q-24) : file de travail filtrable par statut — propre à l'avocat pour ROLE_AVOCAT, transverse pour le personnel interne. */
    @Transactional(readOnly = true)
    public PageResponse<DemandeFraisResponse> lister(StatutCircuitFrais statut, Long dossierId, Long avocatId,
                                                       String referenceFacture, LocalDate dateDebut, LocalDate dateFin,
                                                       Pageable pageable) {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        /*
         * Pour un avocat, l'identifiant est **imposé**, jamais reçu : il ne lit que ses propres
         * demandes. Un `avocatId` laissé libre aurait suffi à lire celles d'un confrère en changeant
         * un chiffre dans l'URL — le filtre d'instruction et la restriction d'accès ne peuvent pas
         * être le même paramètre.
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
        // Une requête pour les décisions de toute la page, un seul seuil global (Q-82).
        Map<Long, List<ValidationFrais>> validations = validationFraisRepository
                .findByDemandeFraisIdInOrderByDateValidationAscIdAsc(page.map(DemandeFrais::getId).getContent())
                .stream()
                .collect(Collectors.groupingBy(v -> v.getDemandeFrais().getId()));
        double seuilGlobal = page.isEmpty() ? 0 : seuilGlobal();
        // Pas d'URL pré-signée sur la liste : elles ne vivent que 10 minutes et coûteraient un
        // aller-retour MinIO par pièce, pour un écran qui ne les affiche pas (Q-97).
        return PageResponse.of(page,
                d -> versReponse(d, validations.getOrDefault(d.getId(), List.of()), seuilGlobal, List.of()));
    }

    /**
     * 🆕 M16 (Q-82) : détail d'une demande et décisions du circuit (UC-INT-02 étape 3 : « la demande,
     * ses pièces justificatives et le résultat du contrôle de conformité »). L'avocat ne lit que ses
     * propres demandes, comme dans la file (Q-24).
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
     * Rejet terminal d'une demande, quelle que soit l'étape (RG-INT-03) : l'avocat est notifié à
     * chaque fois — seul FR-INT-02 le mentionne explicitement dans le texte fonctionnel, mais rien
     * ne justifierait de le priver de cette information aux étapes suivantes (RG-INT-03 étant la
     * même règle pour FR-INT-02/03/04) ; complété par cohérence (Q-54).
     */
    private void rejeter(DemandeFrais demande, Utilisateur decideur, EtapeValidationFrais etape, String motif) {
        enregistrerValidation(demande, etape, decideur, DecisionValidationFrais.REJET, motif);
        demande.setStatutCircuit(StatutCircuitFrais.REJETEE);
        demande.setMotifRejet(motif);
        demandeFraisRepository.save(demande);

        String message = "Votre demande de frais " + demande.getId() + " sur le dossier "
                + demande.getDossier().getReference() + " a été rejetée : " + motif;
        compteAvocatService.utilisateurDe(demande.getAvocat())
                .ifPresent(u -> alerteService.declencher(TypeAlerte.NOUVELLE_DEMANDE_FRAIS, demande.getDossier().getId(), u, message));
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

    /** RG-ALR : signale à DJ et DJA qu'une validation conjointe est requise (FR-INT-03, dossier sensible ou montant &gt; seuil). */
    private void signalerValidationConjointeRequise(DemandeFrais demande) {
        String message = "La demande de frais " + demande.getId() + " sur le dossier "
                + demande.getDossier().getReference() + " requiert une validation conjointe DJ+DJA (RG-INT-03).";
        notifierProfil("DJ", demande.getDossier().getId(), TypeAlerte.VALIDATION_CONJOINTE_REQUISE, message);
        notifierProfil("DJA", demande.getDossier().getId(), TypeAlerte.VALIDATION_CONJOINTE_REQUISE, message);
    }

    private void notifierProfil(String profilCode, Long dossierId, TypeAlerte type, String declencheur) {
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue(profilCode);
        destinataires.forEach(destinataire -> alerteService.declencher(type, dossierId, destinataire, declencheur));
    }

    /** Q-26 : seuil du dossier si dérogation validée, sinon seuil global. */
    private static double seuilApplicable(Dossier dossier, double seuilGlobal) {
        if (dossier.getSeuilOrigine() == SeuilOrigine.DEROGATION && dossier.getSeuilMontant() != null) {
            return dossier.getSeuilMontant();
        }
        return seuilGlobal;
    }

    private double seuilGlobal() {
        return Double.parseDouble(configurationService.getOrThrow(CLE_SEUIL_MONTANT_DEFAUT).getValeur());
    }

    /** RG-INT-03 : {@code estSensible = true} OU {@code montant > seuilApplicable}. */
    private static boolean exigeValidationConjointe(Dossier dossier, Double montant, double seuil) {
        return Boolean.TRUE.equals(dossier.getEstSensible()) || (montant != null && montant > seuil);
    }

    private DemandeFraisResponse versReponse(DemandeFrais demande) {
        return versReponse(demande,
                validationFraisRepository.findByDemandeFraisIdOrderByDateValidationAscIdAsc(demande.getId()),
                seuilGlobal(), List.of());
    }

    /**
     * UC-INT-02 étape 3 (Q-97) : l'instructeur doit <b>voir</b> les pièces, pas en lire les noms.
     * Seules les entrées déposées par le portail sont signées — le tableau peut contenir des
     * libellés antérieurs au téléversement, qui ne désignent aucun objet stocké et pour lesquels
     * une URL pré-signée échouerait.
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

    /** La clé est {@code frais/{uuid}-{nom}} : seul le nom d'origine est lisible par un humain. */
    private static String nomDOrigine(String cle) {
        String fin = cle.substring(cle.lastIndexOf('/') + 1);
        int tiret = fin.indexOf('-', 35);
        return tiret >= 0 ? fin.substring(tiret + 1) : fin;
    }

    /**
     * Q-82 : {@code validationConjointeRequise} applique la règle en vigueur (RG-INT-03) — pour une
     * demande qui n'a pas encore atteint l'opportunité, c'est ce qui l'attend.
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
