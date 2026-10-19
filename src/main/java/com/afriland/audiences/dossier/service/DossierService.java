package com.afriland.audiences.dossier.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueAction;
import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.reference.FamilleReference;
import com.afriland.audiences.common.reference.GenerateurReference;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.domain.AuditSeuilDossier;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierAvocat;
import com.afriland.audiences.dossier.domain.DossierAvocatId;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.domain.DossierJuristeId;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.SeuilOrigine;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.domain.StatutValidation;
import com.afriland.audiences.dossier.domain.TypeEtape;
import com.afriland.audiences.dossier.mapper.AuditSeuilMapper;
import com.afriland.audiences.dossier.mapper.DossierMapper;
import com.afriland.audiences.dossier.mapper.EtapeProcedureMapper;
import com.afriland.audiences.dossier.mapper.HistoriqueActionMapper;
import com.afriland.audiences.dossier.repository.AuditSeuilDossierRepository;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.dossier.web.dto.AuditSeuilResponse;
import com.afriland.audiences.dossier.web.dto.CreerDossierRequest;
import com.afriland.audiences.dossier.web.dto.CreerEtapeRequest;
import com.afriland.audiences.dossier.web.dto.DemandeDerogationSeuilRequest;
import com.afriland.audiences.dossier.web.dto.DossierResponse;
import com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse;
import com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse;
import com.afriland.audiences.dossier.web.dto.ModifierAffectationRequest;
import com.afriland.audiences.dossier.web.dto.ModifierSensibiliteRequest;
import com.afriland.audiences.dossier.web.dto.ProposerSensibiliteRequest;
import com.afriland.audiences.dossier.web.dto.ModifierStatutEtapeRequest;
import com.afriland.audiences.dossier.web.dto.ValiderDerogationRequest;
import com.afriland.audiences.dossier.web.dto.ValiderSensibiliteRequest;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.domain.Client;
import com.afriland.audiences.referentiel.domain.NatureDossier;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.ClientRepository;
import com.afriland.audiences.referentiel.repository.NatureDossierRepository;
import com.afriland.audiences.referentiel.repository.TypeClientSensibleRepository;
import com.afriland.audiences.referentiel.service.AgenceService;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.web.dto.VueClientDossiersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DossierService {

    private final DossierRepository dossierRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;
    private final DossierJuristeRepository dossierJuristeRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final AuditSeuilDossierRepository auditSeuilDossierRepository;
    /** History entries embedded in the case page; beyond that, the tab paginates. */
    private static final int TAILLE_HISTORIQUE_FICHE = 10;

    private final HistoriqueActionRepository historiqueActionRepository;
    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AvocatRepository avocatRepository;
    private final NotificationPrestataireService notificationPrestataireService;
    private final NatureDossierRepository natureDossierRepository;
    private final TypeClientSensibleRepository typeClientSensibleRepository;
    private final EtapeTransitionValidator transitionValidator;
    private final DossierMapper dossierMapper;
    private final EtapeProcedureMapper etapeProcedureMapper;
    private final AuditSeuilMapper auditSeuilMapper;
    private final HistoriqueActionMapper historiqueActionMapper;
    private final HistoriqueActionService historiqueActionService;
    private final CurrentUserService currentUserService;
    private final AlerteService alerteService;
    private final AgenceService agenceService;
    private final GenerateurReference generateurReference;
    /*
     * Factual transition conditions, provided by the modules that own the facts. Spring collects
     * every implementation; the list may be empty.
     */
    private final List<ConditionTransitionEtape> conditionsTransition;

    // ------------------------------------------------------------------ Creation

    @Transactional
    public DossierResponse creerDossier(CreerDossierRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Client introuvable : " + request.clientId()));

        CategorieDossier categorie = resoudreCategorie(request);
        /*
         * The conditional references (credit file, transfer report, account incident, supporting
         * evidence) are not required: the attachments are mandatory, not their text. That cannot be
         * enforced here since attachments are uploaded after creation, so the screen enforces it.
         */
        // Validated whether or not `estSensible` is set, otherwise an invalid value could slip
        // through outside the sensitive branch.
        if (StringUtils.hasText(request.typeClientSensible())) {
            validerTypeClientSensible(request.typeClientSensible());
        }

        /*
         * Both branch codes must exist in the branch reference data. The origin code is mandatory,
         * the litigation code optional but validated when provided. Checked here rather than by
         * annotation because it queries the database.
         */
        validerAgence(request.codeAgenceOrigine(), "codeAgenceOrigine");
        if (StringUtils.hasText(request.codeAgenceContentieuse())) {
            validerAgence(request.codeAgenceContentieuse(), "codeAgenceContentieuse");
        }

        /*
         * The reference is assigned by the system, only once all checks have passed: an issued
         * number is never returned, so requesting it earlier would leave gaps in the sequence.
         */
        String reference = referenceAttribuee();

        Dossier dossier = Dossier.builder()
                .reference(reference)
                .nature(request.nature())
                .categorie(categorie)
                .juridictionSaisie(request.juridictionSaisie())
                .client(client)
                .parties(dossierMapper.toParties(request.parties()))
                .risqueEncouru(request.risqueEncouru())
                .recoursExerces(request.recoursExerces())
                .motifRenvoi(request.motifRenvoi())
                .codeAgenceOrigine(request.codeAgenceOrigine())
                .codeAgenceContentieuse(request.codeAgenceContentieuse())
                .dossierCreditOrigine(request.dossierCreditOrigine())
                .pvTransfert(request.pvTransfert())
                .incidentCompteReference(request.incidentCompteReference())
                .elementsJustificatifs(request.elementsJustificatifs())
                .champAlarme(request.champAlarme())
                .estSensible(Boolean.TRUE.equals(request.estSensible()))
                .typeClientSensible(request.typeClientSensible())
                .seuilOrigine(SeuilOrigine.GLOBAL)
                .sensibiliteStatut(StatutValidation.EN_ATTENTE)
                .build();
        dossier = dossierRepository.save(dossier);

        EtapeProcedure instance = creerEtapeInterne(dossier, TypeEtape.INSTANCE, StatutCycleVie.OUVERTURE);

        List<Long> juristesAffectes = affecterJuristes(dossier, request.juristesAffectes());
        List<Long> avocatsAffectes = affecterAvocats(dossier, request.avocatsAffectes());

        if (Boolean.TRUE.equals(request.estSensible())) {
            if (request.seuilMontant() == null || !StringUtils.hasText(request.typeClientSensible())) {
                throw ApiException.badRequest(ErrorCodes.METADONNEES_MANQUANTES,
                        "seuilMontant et typeClientSensible sont obligatoires pour un dossier à diligence particulière");
            }
            creerPropositionSeuil(dossier, request.seuilMontant(),
                    "Proposition initiale de diligence particulière à la création du dossier");
        }

        Utilisateur auteur = currentUserService.getCurrentUser();
        historiqueActionService.enregistrerPour(auteur, dossier.getId(), TypeActionHistorique.DOSSIER_CREE,
                Map.of("reference", dossier.getReference(), "categorie", categorie.name()));

        return construireReponse(dossier, List.of(instance), juristesAffectes, avocatsAffectes, List.of());
    }

    /**
     * Reference of the next case.
     * <p>The uniqueness check now detects a <b>numbering anomaly</b> (a reference in the assigned
     * format existing without the counter knowing it), with a clear message instead of a database
     * constraint error. No field is named in the error since there is nothing to correct on screen.
     */
    private String referenceAttribuee() {
        String reference = generateurReference.prochaine(FamilleReference.DOSSIER);
        if (dossierRepository.existsByReference(reference)) {
            throw ApiException.conflict(ErrorCodes.REFERENCE_DOSSIER_DUPLIQUEE,
                    "La référence attribuée '" + reference + "' est déjà portée par un dossier : "
                            + "le compteur de références est désynchronisé (V36)");
        }
        return reference;
    }

    /**
     * Whether this code designates an open branch in the reference data. The message names the
     * field so the form can display it in the right place, and quotes the received code.
     */
    private void validerAgence(String code, String champ) {
        if (!agenceService.existeEtEstActive(code)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Aucune agence ouverte ne porte le code « " + code + " » (" + champ + ")");
        }
    }

    /**
     * {@code dossier.type_client_sensible} has no foreign key, so the service validates it, as for
     * {@code nature}. The check is on the <b>code</b>, not the label: unique, stable if the label
     * is reworded, and it fits the column.
     */
    private void validerTypeClientSensible(String code) {
        typeClientSensibleRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                        "Type de client inconnu du référentiel : " + code));
    }

    private CategorieDossier resoudreCategorie(CreerDossierRequest request) {
        NatureDossier natureRef = natureDossierRepository.findByLibelleIgnoreCase(request.nature())
                .orElseThrow(() -> ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                        "Nature de dossier inconnue de la taxonomie (Annexe A) : " + request.nature()));
        CategorieDossier deduite = natureRef.getCategorie();
        if (request.categorie() != null && request.categorie() != deduite) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La catégorie soumise (" + request.categorie() + ") est incohérente avec la nature '"
                            + request.nature() + "', dont la catégorie est " + deduite + " (RG-DOS-02)");
        }
        return deduite;
    }

    private List<Long> affecterJuristes(Dossier dossier, List<Long> juristeIds) {
        Utilisateur auteur = currentUserService.getCurrentUser();
        return juristeIds.stream().distinct().map(juristeId -> {
            Utilisateur juriste = utilisateurRepository.findById(juristeId)
                    .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                            "Juriste introuvable : " + juristeId));
            DossierJuriste affectation = DossierJuriste.builder()
                    .id(new DossierJuristeId(dossier.getId(), juristeId))
                    .dossier(dossier)
                    .utilisateur(juriste)
                    .affectePar(auteur)
                    .build();
            dossierJuristeRepository.save(affectation);
            return juristeId;
        }).toList();
    }

    private List<Long> affecterAvocats(Dossier dossier, List<Long> avocatIds) {
        Utilisateur auteur = currentUserService.getCurrentUser();
        return avocatIds.stream().distinct().map(avocatId -> {
            Avocat avocat = avocatRepository.findById(avocatId)
                    .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                            "Avocat introuvable : " + avocatId));
            DossierAvocat affectation = DossierAvocat.builder()
                    .id(new DossierAvocatId(dossier.getId(), avocatId))
                    .dossier(dossier)
                    .avocat(avocat)
                    .affectePar(auteur)
                    .build();
            dossierAvocatRepository.save(affectation);
            // The lawyer is notified through external channels that a case was assigned to them.
            notificationPrestataireService.notifier(EvenementPrestataire.AFFECTATION_DOSSIER, avocat,
                    dossier.getId(), "affectation-" + dossier.getId());
            return avocatId;
        }).toList();
    }

    // ------------------------------------------------------------------ Consultation

    @Transactional(readOnly = true)
    public PageResponse<DossierResponse> listerDossiers(String nature, CategorieDossier categorie,
                                                          String juridiction, String reference,
                                                          Long clientId, boolean mesDossiers,
                                                          Boolean estSensible, StatutValidation sensibiliteStatut,
                                                          TypeEtape typeEtape, StatutCycleVie statutEtape,
                                                          LocalDate dateDebut, LocalDate dateFin,
                                                          Pageable pageable) {
        // A reversed window would return nothing: reject it rather than suggest no hearing took
        // place.
        if (dateDebut != null && dateFin != null && dateDebut.isAfter(dateFin)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début doit précéder la date de fin");
        }
        // "My cases" is resolved from the authenticated user, never from a received id, so it
        // cannot expose a colleague's portfolio.
        Long juristeId = mesDossiers ? currentUserService.getCurrentUser().getId() : null;
        var page = dossierRepository.findAll(
                DossierSpecifications.withFilters(nature, categorie, juridiction, reference, clientId, juristeId,
                        estSensible, sensibiliteStatut, typeEtape, statutEtape, dateDebut, dateFin),
                pageable);
        // Three queries for the whole page, not three per row.
        return new PageResponse<>(construireReponses(page.getContent()), page.getTotalPages(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DossierResponse consulterDossier(Long id) {
        Dossier dossier = getDossierOrThrow(id);
        List<EtapeProcedure> etapes = etapeProcedureRepository.findByDossierIdOrderByTypeAsc(id);
        List<Long> juristesAffectes = dossierJuristeRepository.findByDossierId(id).stream()
                .map(dj -> dj.getUtilisateur().getId()).toList();
        List<Long> avocatsAffectes = dossierAvocatRepository.findByDossierId(id).stream()
                .map(da -> da.getAvocat().getId()).toList();
        /*
         * The case page only carries the first page of history (RG-DOS-06 records every action, so
         * a long-running case can have hundreds). The `historique` field keeps its shape; the tab
         * paginates via `GET /dossiers/{id}/historique`.
         */
        List<HistoriqueAction> historique = historiqueActionRepository
                .findByDossierIdOrderByDateActionDesc(id, PageRequest.of(0, TAILLE_HISTORIQUE_FICHE)).getContent();
        return construireReponse(dossier, etapes, juristesAffectes, avocatsAffectes, historique);
    }

    /** Full paginated history of a case, for the history tab. */
    @Transactional(readOnly = true)
    public PageResponse<HistoriqueActionResponse> historique(Long id, Pageable pageable) {
        getDossierOrThrow(id);
        return PageResponse.of(historiqueActionRepository.findByDossierIdOrderByDateActionDesc(id, pageable),
                historiqueActionMapper::toResponse);
    }

    private Dossier getDossierOrThrow(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Dossier introuvable : " + id));
    }

    private DossierResponse construireReponse(Dossier dossier, List<EtapeProcedure> etapes,
                                               List<Long> juristesAffectes, List<Long> avocatsAffectes,
                                               List<HistoriqueAction> historique) {
        List<EtapeProcedureResponse> etapesResponse = etapes.stream().map(etapeProcedureMapper::toResponse).toList();
        var historiqueResponse = historique.stream().map(historiqueActionMapper::toResponse).toList();
        return dossierMapper.toResponse(dossier, etapesResponse, juristesAffectes, avocatsAffectes, historiqueResponse);
    }

    // ------------------------------------------------------------------ Consolidated view by
    // client

    /** FR-REP-03, RG-REP-04: independent of the current in-house lawyer's assignments. */
    @Transactional(readOnly = true)
    public VueClientDossiersResponse vueConsolideeParClient(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Client introuvable : " + clientId));
        List<Dossier> dossiers = dossierRepository.findByClientId(clientId);
        List<DossierResponse> recouvrement = construireReponses(dossiers.stream()
                .filter(d -> d.getCategorie() == CategorieDossier.RECOUVREMENT).toList());
        List<DossierResponse> exploitationLitiges = construireReponses(dossiers.stream()
                .filter(d -> d.getCategorie() == CategorieDossier.EXPLOITATION_LITIGES).toList());
        return new VueClientDossiersResponse(clientId, client.getNom(), recouvrement, exploitationLitiges);
    }

    /**
     * Builds the response for a <b>list</b> of cases in three queries, whatever its size. The
     * contract requires the full unpaginated list and screen 13 reads each case's current step, so
     * the number of queries had to be reduced rather than the response.
     */
    private List<DossierResponse> construireReponses(List<Dossier> dossiers) {
        if (dossiers.isEmpty()) {
            return List.of();
        }
        List<Long> ids = dossiers.stream().map(Dossier::getId).toList();

        Map<Long, List<EtapeProcedure>> etapesParDossier =
                etapeProcedureRepository.findByDossierIdInOrderByDossierIdAscTypeAsc(ids).stream()
                        .collect(Collectors.groupingBy(etape -> etape.getDossier().getId()));
        // The composite id already holds `dossierId`: reading it initialises no proxy.
        Map<Long, List<Long>> juristesParDossier = dossierJuristeRepository.findByDossierIdIn(ids).stream()
                .collect(Collectors.groupingBy(dj -> dj.getId().getDossierId(),
                        Collectors.mapping(dj -> dj.getId().getUtilisateurId(), Collectors.toList())));
        Map<Long, List<Long>> avocatsParDossier = dossierAvocatRepository.findByDossierIdIn(ids).stream()
                .collect(Collectors.groupingBy(da -> da.getId().getDossierId(),
                        Collectors.mapping(da -> da.getId().getAvocatId(), Collectors.toList())));

        return dossiers.stream()
                .map(dossier -> construireReponse(dossier,
                        etapesParDossier.getOrDefault(dossier.getId(), List.of()),
                        juristesParDossier.getOrDefault(dossier.getId(), List.of()),
                        avocatsParDossier.getOrDefault(dossier.getId(), List.of()),
                        List.of()))
                .toList();
    }

    // ------------------------------------------------------------------ Assignment

    @Transactional
    public DossierResponse modifierAffectation(Long dossierId, ModifierAffectationRequest request, boolean forcer) {
        Dossier dossier = getDossierOrThrow(dossierId);

        List<Long> juristesDemandes = request.juristesAffectes() != null ? request.juristesAffectes() : List.of();
        List<Long> avocatsDemandes = request.avocatsAffectes() != null ? request.avocatsAffectes() : List.of();

        List<Long> juristesExistants = dossierJuristeRepository.findByDossierId(dossierId).stream()
                .map(dj -> dj.getUtilisateur().getId()).toList();
        List<Long> avocatsExistants = dossierAvocatRepository.findByDossierId(dossierId).stream()
                .map(da -> da.getAvocat().getId()).toList();

        boolean doublonJuriste = juristesDemandes.stream().anyMatch(juristesExistants::contains);
        boolean doublonAvocat = avocatsDemandes.stream().anyMatch(avocatsExistants::contains);
        boolean doublon = doublonJuriste || doublonAvocat;
        if (doublon && !forcer) {
            throw ApiException.conflict(ErrorCodes.DOUBLON_AFFECTATION,
                    "Au moins un juriste ou avocat est déjà affecté à ce dossier ; ajoutez ?forcer=true pour confirmer (RG-DOS-07)");
        }
        if (doublon) {
            // Duplicate confirmed by the in-house lawyer: supervision alert to active DJ users, in
            // addition to the history entry below.
            signalerDoublonAffectation(dossier);
        }

        dossierJuristeRepository.deleteByDossierId(dossierId);
        dossierAvocatRepository.deleteByDossierId(dossierId);
        List<Long> juristesAffectes = affecterJuristes(dossier, juristesDemandes);
        List<Long> avocatsAffectes = affecterAvocats(dossier, avocatsDemandes);

        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.DOSSIER_AFFECTATION_MODIFIEE,
                Map.of("juristesAffectes", juristesAffectes, "avocatsAffectes", avocatsAffectes));

        List<EtapeProcedure> etapes = etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossierId);
        return construireReponse(dossier, etapes, juristesAffectes, avocatsAffectes, List.of());
    }

    /** Recipients are DJ users (supervision of confirmed duplicates). */
    private void signalerDoublonAffectation(Dossier dossier) {
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue("DJ");
        String declencheur = "Doublon d'affectation confirmé sur le dossier " + dossier.getReference()
                + " (RG-DOS-07)";
        destinataires.forEach(dj -> alerteService.declencher(TypeAlerte.DOUBLON_AFFECTATION,
                dossier.getId(), dj, declencheur));
    }

    // ------------------------------------------------------------------ Step lifecycle

    @Transactional
    public EtapeProcedureResponse modifierStatutEtape(Long dossierId, Long etapeId, ModifierStatutEtapeRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        EtapeProcedure etape = etapeProcedureRepository.findById(etapeId)
                .filter(e -> e.getDossier().getId().equals(dossierId))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Étape introuvable pour ce dossier : " + etapeId));

        StatutCycleVie ancien = etape.getStatutCycleVie();
        StatutCycleVie nouveau = request.nouveauStatut();
        if (!transitionValidator.estAutorisee(ancien, nouveau)) {
            throw ApiException.badRequest(ErrorCodes.TRANSITION_NON_AUTORISEE,
                    "Transition " + ancien + " -> " + nouveau + " non autorisée pour l'étape " + etape.getType());
        }
        verifierLesFaits(etape, ancien, nouveau);

        etape.setStatutCycleVie(nouveau);
        etape = etapeProcedureRepository.save(etape);

        // The step goes into `details` so it can be queried (e.g. all moves to EN_DELIBERE in a
        // quarter).
        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.ETAPE_STATUT_MODIFIE,
                Map.of("etape", etape.getType().name(), "ancienStatut", ancien.name(), "nouveauStatut", nouveau.name()));

        if (transitionValidator.estExerciceDeRecours(ancien, nouveau)) {
            ouvrirEtapeSuivante(dossier, etape);
        }

        return etapeProcedureMapper.toResponse(etape);
    }

    /**
     * Factual conditions of the transition (status order is checked just before): e.g. a held
     * hearing before deliberation, an unfavourable judgment before an appeal, fees paid before
     * archiving.
     * <p><b>All conditions are evaluated</b>, so the user sees everything left to do at once.
     * <p>The error code stays {@code ERR-004}: for the caller the transition is refused either way;
     * only the message differs.
     */
    private void verifierLesFaits(EtapeProcedure etape, StatutCycleVie ancien, StatutCycleVie nouveau) {
        List<String> empechements = conditionsTransition.stream()
                .filter(condition -> condition.concerne(ancien, nouveau))
                .map(condition -> condition.empechement(etape))
                .flatMap(Optional::stream)
                .toList();

        if (!empechements.isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.TRANSITION_NON_AUTORISEE,
                    String.join(" ", empechements));
        }
    }

    private void ouvrirEtapeSuivante(Dossier dossier, EtapeProcedure etapeActuelle) {
        TypeEtape suivante;
        try {
            suivante = etapeActuelle.getType().suivante();
        } catch (IllegalStateException e) {
            throw ApiException.badRequest(ErrorCodes.TRANSITION_NON_AUTORISEE,
                    "Aucune voie de recours supplémentaire disponible après " + etapeActuelle.getType());
        }
        if (etapeProcedureRepository.existsByDossierIdAndType(dossier.getId(), suivante)) {
            return;
        }
        EtapeProcedure nouvelle = creerEtapeInterne(dossier, suivante, StatutCycleVie.OUVERTURE);
        historiqueActionService.enregistrer(dossier.getId(), TypeActionHistorique.ETAPE_OUVERTE_AUTOMATIQUEMENT,
                Map.of("etape", suivante.name(), "etapeId", nouvelle.getId()));
    }

    @Transactional
    public EtapeProcedureResponse creerEtape(Long dossierId, CreerEtapeRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        if (etapeProcedureRepository.existsByDossierIdAndType(dossierId, request.type())) {
            throw ApiException.conflictSurChamp(ErrorCodes.CONFLIT,
                    "L'étape " + request.type() + " existe déjà pour ce dossier", "type");
        }
        // Direct positioning (UC-DOS-04 alt. 3-b): creates missing intermediate steps in OUVERTURE,
        // without detailed history (RG-DOS-04).
        for (TypeEtape intermediaire : etapesPrecedentes(request.type())) {
            if (!etapeProcedureRepository.existsByDossierIdAndType(dossierId, intermediaire)) {
                creerEtapeInterne(dossier, intermediaire, StatutCycleVie.OUVERTURE);
            }
        }
        EtapeProcedure etape = creerEtapeInterne(dossier, request.type(), StatutCycleVie.OUVERTURE);
        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.ETAPE_POSITIONNEE_DIRECTEMENT,
                Map.of("etape", request.type().name(), "etapeId", etape.getId()));
        return etapeProcedureMapper.toResponse(etape);
    }

    /**
     * Steps that direct positioning must create if missing (UC-DOS-04 alt. 3-b). The CCJA appeal
     * has the same predecessors as the national one (Instance and Appeal), <b>not</b>
     * {@code RECOURS_2}: the two cassation appeals are concurrent.
     */
    private List<TypeEtape> etapesPrecedentes(TypeEtape type) {
        return switch (type) {
            case INSTANCE -> List.of();
            case RECOURS_1 -> List.of(TypeEtape.INSTANCE);
            case RECOURS_2, RECOURS_2_CCJA -> List.of(TypeEtape.INSTANCE, TypeEtape.RECOURS_1);
        };
    }

    private EtapeProcedure creerEtapeInterne(Dossier dossier, TypeEtape type, StatutCycleVie statut) {
        EtapeProcedure etape = EtapeProcedure.builder()
                .dossier(dossier)
                .type(type)
                .statutCycleVie(statut)
                .build();
        return etapeProcedureRepository.save(etape);
    }

    // ------------------------------------------------------------------ Threshold override

    @Transactional
    public AuditSeuilResponse demanderDerogationSeuil(Long dossierId, DemandeDerogationSeuilRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        if (!Boolean.TRUE.equals(dossier.getEstSensible()) || dossier.getSensibiliteStatut() != StatutValidation.VALIDEE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Une dérogation de seuil ne peut être demandée que sur un dossier à diligence particulière déjà validé");
        }
        AuditSeuilDossier audit = creerPropositionSeuil(dossier, request.nouveauSeuil(), request.motif());
        return auditSeuilMapper.toResponse(audit);
    }

    private AuditSeuilDossier creerPropositionSeuil(Dossier dossier, Double nouveauSeuil, String motif) {
        Utilisateur auteur = currentUserService.getCurrentUser();
        AuditSeuilDossier audit = AuditSeuilDossier.builder()
                .dossier(dossier)
                .utilisateur(auteur)
                .ancienSeuil(dossier.getSeuilMontant())
                .nouveauSeuil(nouveauSeuil)
                .motif(motif)
                .statut(StatutValidation.EN_ATTENTE)
                .build();
        audit = auditSeuilDossierRepository.save(audit);
        historiqueActionService.enregistrerPour(auteur, dossier.getId(), TypeActionHistorique.DEROGATION_SEUIL_DEMANDEE,
                Map.of("nouveauSeuil", nouveauSeuil, "motif", motif));
        return audit;
    }

    /**
     * Threshold requests of a case, most recent first. Exposes the request id needed for
     * arbitration. Includes the initial proposal made at case creation.
     */
    @Transactional(readOnly = true)
    public List<AuditSeuilResponse> listerDerogationsSeuil(Long dossierId, StatutValidation statut) {
        getDossierOrThrow(dossierId);
        List<AuditSeuilDossier> audits = statut == null
                ? auditSeuilDossierRepository.findByDossierIdOrderByDateActionDesc(dossierId)
                : auditSeuilDossierRepository.findByDossierIdAndStatutOrderByDateActionDesc(dossierId, statut);
        return audits.stream().map(auditSeuilMapper::toResponse).toList();
    }

    @Transactional
    public AuditSeuilResponse validerDerogationSeuil(Long dossierId, Long auditId, ValiderDerogationRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        /*
         * While sensitivity is not validated, the only pending audit is the initial proposal,
         * decided through sensitivity validation (which also updates the case status). Arbitrating
         * it here would set a threshold while leaving sensitivity pending.
         */
        if (dossier.getSensibiliteStatut() != StatutValidation.VALIDEE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La proposition initiale se tranche par la validation de la diligence particulière, pas comme une dérogation");
        }
        AuditSeuilDossier audit = auditSeuilDossierRepository.findById(auditId)
                .filter(a -> a.getDossier().getId().equals(dossierId))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Demande de dérogation introuvable : " + auditId));
        if (audit.getStatut() != StatutValidation.EN_ATTENTE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Cette demande a déjà été traitée");
        }
        if (request.statut() == StatutValidation.REJETEE && !StringUtils.hasText(request.motif())) {
            throw ApiException.badRequest(ErrorCodes.METADONNEES_MANQUANTES, "Le motif est obligatoire en cas de rejet");
        }

        Utilisateur validateur = currentUserService.getCurrentUser();
        audit.setStatut(request.statut());
        audit.setValidePar(validateur);
        auditSeuilDossierRepository.save(audit);

        if (request.statut() == StatutValidation.VALIDEE) {
            dossier.setSeuilMontant(audit.getNouveauSeuil());
            dossier.setSeuilOrigine(SeuilOrigine.DEROGATION);
            dossierRepository.save(dossier);
        }

        // The decision outcome is stored as data so that, e.g., all rejected overrides can be
        // queried.
        historiqueActionService.enregistrerPour(validateur, dossierId, TypeActionHistorique.DEROGATION_SEUIL_ARBITREE,
                Map.of("decision", request.statut().name(), "auditId", auditId,
                        "motif", Optional.ofNullable(request.motif()).orElse("")));

        return auditSeuilMapper.toResponse(audit);
    }

    // ------------------------------------------------------------------ Sensitivity validation

    /**
     * The in-house lawyer proposes special diligence on an already open case.
     * <p>Same workflow as on creation: an {@code EN_ATTENTE} proposal, then decision by DJ or DJA
     * through the same endpoint and queue.
     * <p><b>No effect before validation</b>: threshold and origin only change on arbitration, so a
     * pending proposal does not trigger double fee validation.
     */
    @Transactional
    public DossierResponse proposerSensibilite(Long dossierId, ProposerSensibiliteRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);

        if (Boolean.TRUE.equals(dossier.getEstSensible())) {
            // Two distinct refusals: the user must know whether to wait or whether there is nothing
            // to do.
            String raison = dossier.getSensibiliteStatut() == StatutValidation.EN_ATTENTE
                    ? "Une proposition de diligence particulière est déjà en attente d'arbitrage sur ce dossier"
                    : "Ce dossier est déjà suivi en diligence particulière";
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, raison);
        }
        validerTypeClientSensible(request.typeClientSensible());

        dossier.setEstSensible(true);
        dossier.setSensibiliteStatut(StatutValidation.EN_ATTENTE);
        dossier.setTypeClientSensible(request.typeClientSensible());
        /*
         * Reasons from the previous episode are cleared so the screen does not show a decision that
         * no longer applies.
         */
        dossier.setSensibiliteMotifRejet(null);
        dossier.setSensibiliteMotifRetrait(null);
        dossier.setSensibiliteValidePar(null);
        dossier = dossierRepository.save(dossier);

        // The threshold proposal follows the same path as on creation, so arbitration will find it.
        creerPropositionSeuil(dossier, request.seuilMontant(), request.motif());

        Utilisateur auteur = currentUserService.getCurrentUser();
        historiqueActionService.enregistrerPour(auteur, dossierId, TypeActionHistorique.SENSIBILITE_PROPOSEE,
                Map.of("seuilMontant", request.seuilMontant(),
                        "typeClientSensible", request.typeClientSensible(),
                        "motif", request.motif()));

        return construireReponseComplete(dossier);
    }

    /**
     * DJ or DJA sets or lifts special diligence directly, without a proposal.
     * <p><b>Removal restores the global threshold</b>: an override threshold only exists for a case
     * under special diligence.
     * <p><b>A reason is mandatory on removal only</b>: removal takes the case out of the double fee
     * validation workflow, and the audit will ask why.
     */
    @Transactional
    public DossierResponse modifierSensibilite(Long dossierId, ModifierSensibiliteRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        Utilisateur decideur = currentUserService.getCurrentUser();

        if (Boolean.TRUE.equals(request.estSensible())) {
            if (request.seuilMontant() == null || !StringUtils.hasText(request.typeClientSensible())) {
                throw ApiException.badRequest(ErrorCodes.METADONNEES_MANQUANTES,
                        "seuilMontant et typeClientSensible sont obligatoires pour un dossier à diligence particulière");
            }
            validerTypeClientSensible(request.typeClientSensible());
            if (Boolean.TRUE.equals(dossier.getEstSensible())
                    && dossier.getSensibiliteStatut() == StatutValidation.VALIDEE) {
                throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                        "Ce dossier est déjà suivi en diligence particulière");
            }

            /*
             * A pending proposal from the in-house lawyer is settled by this direct decision, with
             * the threshold given by the DJ.
             */
            auditSeuilDossierRepository
                    .findFirstByDossierIdAndStatutOrderByDateActionAsc(dossierId, StatutValidation.EN_ATTENTE)
                    .ifPresent(audit -> {
                        audit.setStatut(StatutValidation.VALIDEE);
                        audit.setValidePar(decideur);
                        audit.setNouveauSeuil(request.seuilMontant());
                        auditSeuilDossierRepository.save(audit);
                    });

            dossier.setEstSensible(true);
            dossier.setSensibiliteStatut(StatutValidation.VALIDEE);
            dossier.setSensibiliteValidePar(decideur);
            dossier.setTypeClientSensible(request.typeClientSensible());
            dossier.setSeuilMontant(request.seuilMontant());
            dossier.setSeuilOrigine(SeuilOrigine.DEROGATION);
            dossier.setSensibiliteMotifRejet(null);
            dossier.setSensibiliteMotifRetrait(null);
            dossier = dossierRepository.save(dossier);

            historiqueActionService.enregistrerPour(decideur, dossierId,
                    TypeActionHistorique.SENSIBILITE_ARBITREE,
                    Map.of("decision", StatutValidation.VALIDEE.name(),
                            "origine", "DIRECTE",
                            "seuilMontant", request.seuilMontant(),
                            "typeClientSensible", request.typeClientSensible(),
                            "motif", Optional.ofNullable(request.motif()).orElse("")));
            return construireReponseComplete(dossier);
        }

        if (!Boolean.TRUE.equals(dossier.getEstSensible())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Ce dossier n'est pas suivi en diligence particulière : il n'y a rien à retirer");
        }
        if (!StringUtils.hasText(request.motif())) {
            throw ApiException.badRequest(ErrorCodes.METADONNEES_MANQUANTES,
                    "Le motif est obligatoire pour retirer la diligence particulière");
        }

        // A still pending proposal becomes moot: close it rather than leave it open.
        auditSeuilDossierRepository
                .findFirstByDossierIdAndStatutOrderByDateActionAsc(dossierId, StatutValidation.EN_ATTENTE)
                .ifPresent(audit -> {
                    audit.setStatut(StatutValidation.REJETEE);
                    audit.setValidePar(decideur);
                    auditSeuilDossierRepository.save(audit);
                });

        dossier.setEstSensible(false);
        dossier.setSensibiliteStatut(StatutValidation.REJETEE);
        dossier.setSensibiliteValidePar(decideur);
        dossier.setSensibiliteMotifRetrait(request.motif());
        dossier.setSeuilMontant(null);
        dossier.setSeuilOrigine(SeuilOrigine.GLOBAL);
        dossier = dossierRepository.save(dossier);

        historiqueActionService.enregistrerPour(decideur, dossierId, TypeActionHistorique.SENSIBILITE_RETIREE,
                Map.of("motif", request.motif()));

        return construireReponseComplete(dossier);
    }

    /** Full case page: steps and assignments re-read after writing. */
    private DossierResponse construireReponseComplete(Dossier dossier) {
        List<EtapeProcedure> etapes = etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossier.getId());
        List<Long> juristesAffectes = dossierJuristeRepository.findByDossierId(dossier.getId()).stream()
                .map(dj -> dj.getUtilisateur().getId()).toList();
        List<Long> avocatsAffectes = dossierAvocatRepository.findByDossierId(dossier.getId()).stream()
                .map(da -> da.getAvocat().getId()).toList();
        return construireReponse(dossier, etapes, juristesAffectes, avocatsAffectes, List.of());
    }

    @Transactional
    public DossierResponse validerSensibilite(Long dossierId, ValiderSensibiliteRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        if (!Boolean.TRUE.equals(dossier.getEstSensible()) || dossier.getSensibiliteStatut() != StatutValidation.EN_ATTENTE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Aucune proposition de diligence particulière en attente pour ce dossier");
        }
        if (request.statut() == StatutValidation.REJETEE && !StringUtils.hasText(request.motifRejet())) {
            throw ApiException.badRequest(ErrorCodes.METADONNEES_MANQUANTES, "Le motif est obligatoire en cas de rejet");
        }

        Utilisateur validateur = currentUserService.getCurrentUser();
        dossier.setSensibiliteStatut(request.statut());
        dossier.setSensibiliteValidePar(validateur);

        if (request.statut() == StatutValidation.VALIDEE) {
            if (request.typeClientSensibleAjuste() != null) {
                // The DJ adjusts the type when validating: same reference data, same check.
                validerTypeClientSensible(request.typeClientSensibleAjuste());
                dossier.setTypeClientSensible(request.typeClientSensibleAjuste());
            }
            var propositionInitiale = auditSeuilDossierRepository
                    .findFirstByDossierIdAndStatutOrderByDateActionAsc(dossierId, StatutValidation.EN_ATTENTE);
            Double seuilRetenu = request.seuilMontantAjuste() != null
                    ? request.seuilMontantAjuste()
                    : propositionInitiale.map(AuditSeuilDossier::getNouveauSeuil).orElse(null);
            propositionInitiale.ifPresent(a -> {
                a.setStatut(StatutValidation.VALIDEE);
                a.setValidePar(validateur);
                a.setNouveauSeuil(seuilRetenu);
                auditSeuilDossierRepository.save(a);
            });
            dossier.setSeuilMontant(seuilRetenu);
            dossier.setSeuilOrigine(SeuilOrigine.DEROGATION);
        } else {
            dossier.setEstSensible(false);
            dossier.setSensibiliteMotifRejet(request.motifRejet());
        }
        dossier = dossierRepository.save(dossier);

        historiqueActionService.enregistrerPour(validateur, dossierId, TypeActionHistorique.SENSIBILITE_ARBITREE,
                Map.of("decision", request.statut().name(),
                        "motifRejet", Optional.ofNullable(request.motifRejet()).orElse("")));

        List<EtapeProcedure> etapes = etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossierId);
        List<Long> juristesAffectes = dossierJuristeRepository.findByDossierId(dossierId).stream()
                .map(dj -> dj.getUtilisateur().getId()).toList();
        List<Long> avocatsAffectes = dossierAvocatRepository.findByDossierId(dossierId).stream()
                .map(da -> da.getAvocat().getId()).toList();
        return construireReponse(dossier, etapes, juristesAffectes, avocatsAffectes, List.of());
    }
}
