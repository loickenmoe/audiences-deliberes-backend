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
    /** Entrées d'historique embarquées dans la fiche ; au-delà, l'onglet pagine (Q-100). */
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
    private final GenerateurReference generateurReference;
    /*
     * Les conditions de fait, apportées par les modules qui détiennent les faits (2026-09-29).
     * Spring rassemble ici tout ce qui implémente le contrat : cette liste est vide tant qu'aucune
     * condition n'est déclarée, et le comportement reste alors exactement celui d'avant.
     */
    private final List<ConditionTransitionEtape> conditionsTransition;

    // ------------------------------------------------------------------ Création

    @Transactional
    public DossierResponse creerDossier(CreerDossierRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Client introuvable : " + request.clientId()));

        CategorieDossier categorie = resoudreCategorie(request);
        /*
         * Les références conditionnelles (dossier de crédit, PV de transfert, incident de compte,
         * éléments justificatifs) ne sont **plus exigées** — amendement de Q-11, sur arbitrage du
         * métier du 2026-09-29 : « ce sont les pièces jointes qui sont obligatoires, pas leur texte
         * associé ». L'obligation a changé d'objet, elle n'a pas disparu.
         *
         * Elle ne peut pas être tenue ici : une pièce se dépose en GED **après** la création, qui
         * seule produit l'identifiant du dossier. Le serveur ne peut pas exiger un fichier qu'il n'a
         * pas encore reçu. L'écran la tient donc seul — limite acceptée par le métier le même jour,
         * l'alternative étant de refondre la création pour que le dossier naisse avec ses pièces.
         */
        // Le champ est enregistré qu'il soit ou non accompagné de `estSensible` : le valider
        // seulement dans la branche sensible laisserait passer une valeur fantaisiste.
        if (StringUtils.hasText(request.typeClientSensible())) {
            validerTypeClientSensible(request.typeClientSensible());
        }

        /*
         * La référence est attribuée par le système (métier, 2026-09-29). Elle est demandée ici,
         * une fois les refus passés : un numéro servi n'est jamais rendu, et le demander plus tôt
         * en consommerait un à chaque client introuvable ou catégorie incohérente. La suite
         * présenterait des trous que personne ne saurait expliquer.
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
     * Q-06 laisse {@code dossier.type_client_sensible} en VARCHAR libre, non contraint par une clé
     * étrangère — mais confie explicitement la validation au service, comme pour {@code nature}.
     * Elle avait été omise ici : n'importe quelle chaîne était enregistrée en silence, y compris une
     * faute de frappe, et se retrouvait telle quelle dans les écrans et les rapports.
     * <p>
     * Le contrôle porte sur le <b>code</b> et non sur le libellé : il est unique, stable si le
     * libellé est reformulé, et tient dans la colonne — un libellé peut atteindre 100 caractères
     * pour une colonne cible de 50, ce qui reproduirait le défaut corrigé par V7 sur {@code nature}.
     */
    /**
     * La référence du prochain dossier.
     *
     * <p>Le contrôle d'unicité qui précédait la création est conservé, mais il a changé de sens :
     * il ne sanctionne plus une saisie, il détecte une <b>anomalie de numérotation</b>. Il ne peut
     * se déclencher que si une référence au format attribué existait sans que le compteur en ait
     * tenu compte — ce que la reprise de V36 est censée empêcher. Le laisser tomber silencieusement
     * sur la contrainte d'unicité de la base donnerait une erreur technique là où un message clair
     * dit quoi regarder.
     *
     * <p>Aucun champ n'est nommé dans l'erreur : il n'y a plus de champ à corriger à l'écran.
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
            // D19 : l'avocat apprend qu'un dossier lui est confié, par ses canaux externes (Q-118).
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
        // Une fenêtre à l'envers ne renvoie rien du tout : mieux vaut le dire que laisser croire
        // qu'aucun dossier n'a eu d'audience sur la période.
        if (dateDebut != null && dateFin != null && dateDebut.isAfter(dateFin)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début doit précéder la date de fin");
        }
        // « Mes dossiers » se lit sur l'utilisateur authentifié, jamais sur un identifiant reçu :
        // le filtre ne peut donc pas servir à consulter le portefeuille d'un collègue.
        Long juristeId = mesDossiers ? currentUserService.getCurrentUser().getId() : null;
        var page = dossierRepository.findAll(
                DossierSpecifications.withFilters(nature, categorie, juridiction, reference, clientId, juristeId,
                        estSensible, sensibiliteStatut, typeEtape, statutEtape, dateDebut, dateFin),
                pageable);
        // Trois requêtes pour la page entière, et non trois par ligne (QF-41).
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
         * Q-100 : la fiche ne transporte plus que la **première page** de l'historique. RG-DOS-06
         * historise toute action : sur un dossier suivi plusieurs années, `unpaged()` faisait voyager
         * des centaines d'entrées à chaque ouverture, pour un onglet que l'on n'ouvre pas toujours.
         * Le champ `historique` du contrat reste présent et garde sa forme ; il en porte désormais
         * l'entrée la plus récente, et l'onglet pagine par `GET /dossiers/{id}/historique`.
         */
        List<HistoriqueAction> historique = historiqueActionRepository
                .findByDossierIdOrderByDateActionDesc(id, PageRequest.of(0, TAILLE_HISTORIQUE_FICHE)).getContent();
        return construireReponse(dossier, etapes, juristesAffectes, avocatsAffectes, historique);
    }

    /**
     * Historique complet d'un dossier, paginé (Q-100). La fiche n'en embarque que la première page :
     * cet endpoint sert l'onglet, qui seul a besoin de remonter le temps.
     */
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

    // ------------------------------------------------------------------ Vue consolidée par client

    /** FR-REP-03, RG-REP-04 : indépendant de l'affectation du juriste courant. */
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
     * Construit la réponse d'une <b>liste</b> de dossiers en trois requêtes, quel qu'en soit le
     * nombre (QF-41).
     * <p>
     * La version précédente allait chercher étapes, juristes et avocats <b>dossier par dossier</b> :
     * sur la vue consolidée d'un client portant 310 dossiers, cela faisait 930 requêtes et 11 à 19
     * secondes de réponse — assez pour que l'écran reste en chargement et que l'appel monopolise une
     * connexion au détriment des requêtes voisines. Le contrat impose la liste complète, sans
     * pagination, et l'écran 13 lit bien l'étape courante de chaque dossier : c'est donc le nombre de
     * requêtes qu'il fallait réduire, pas la réponse qu'il fallait amputer.
     */
    private List<DossierResponse> construireReponses(List<Dossier> dossiers) {
        if (dossiers.isEmpty()) {
            return List.of();
        }
        List<Long> ids = dossiers.stream().map(Dossier::getId).toList();

        Map<Long, List<EtapeProcedure>> etapesParDossier =
                etapeProcedureRepository.findByDossierIdInOrderByDossierIdAscTypeAsc(ids).stream()
                        .collect(Collectors.groupingBy(etape -> etape.getDossier().getId()));
        // L'identifiant composite porte déjà `dossierId` : le lire n'initialise aucun proxy.
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

    // ------------------------------------------------------------------ Affectation

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
            // Q-12/Q-36 (DECISIONS_AND_OPEN_QUESTIONS.md) : doublon confirmé par le juriste —
            // alerte de supervision au(x) DJ actif(s), en plus de l'historisation ci-dessous.
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

    /** Q-36 : destinataires = utilisateurs de profil DJ (supervision des doublons confirmés). */
    private void signalerDoublonAffectation(Dossier dossier) {
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue("DJ");
        String declencheur = "Doublon d'affectation confirmé sur le dossier " + dossier.getReference()
                + " (RG-DOS-07)";
        destinataires.forEach(dj -> alerteService.declencher(TypeAlerte.DOUBLON_AFFECTATION,
                dossier.getId(), dj, declencheur));
    }

    // ------------------------------------------------------------------ Cycle de vie des étapes

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

        // Q-111 : l'étape descend dans `details`. Noyée dans la phrase, elle n'était pas
        // interrogeable ; ici, « tous les passages en EN_DELIBERE du trimestre » devient une requête.
        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.ETAPE_STATUT_MODIFIE,
                Map.of("etape", etape.getType().name(), "ancienStatut", ancien.name(), "nouveauStatut", nouveau.name()));

        if (transitionValidator.estExerciceDeRecours(ancien, nouveau)) {
            ouvrirEtapeSuivante(dossier, etape);
        }

        return etapeProcedureMapper.toResponse(etape);
    }

    /**
     * Les conditions de fait du passage (métier, 2026-09-29).
     *
     * <p>L'ordre des statuts est vérifié juste avant ; ici on vérifie que ce qui doit avoir eu lieu
     * a bien eu lieu — une audience tenue avant la mise en délibéré, un jugement défavorable avant
     * un recours, des frais soldés avant l'archivage.
     *
     * <p><b>Toutes les conditions sont évaluées, pas seulement la première.</b> S'arrêter au premier
     * refus ferait corriger un manque pour en découvrir un autre au clic suivant : l'utilisateur
     * doit voir d'un coup ce qui lui reste à faire.
     *
     * <p>Le code d'erreur reste {@code ERR-004} : du point de vue de celui qui appelle, le passage
     * est refusé — que ce soit l'ordre ou les faits qui s'y opposent ne change ni son recours ni le
     * traitement côté écran. Seul le message diffère, et c'est lui qu'on lit.
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
        // Positionnement direct (UC-DOS-04 alt. 3-b) : crée les étapes intermédiaires manquantes
        // au statut OUVERTURE, sans historique détaillé pour celles-ci (RG-DOS-04).
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
     * Les étapes que le positionnement direct doit créer si elles manquent (UC-DOS-04 alt. 3-b).
     *
     * <p>Le pourvoi CCJA a les mêmes antécédents que le pourvoi national — l'instance et l'appel —
     * et <b>non</b> {@code RECOURS_2} : les deux cassations sont concurrentes. Positionner un
     * dossier directement en CCJA ne doit pas faire naître une étape Cour Suprême fantôme, qui
     * laisserait croire à un pourvoi qui n'a jamais été formé.
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

    // ------------------------------------------------------------------ Dérogation de seuil

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
     * Demandes de seuil d'un dossier, la plus récente d'abord (Q-74).
     * <p>
     * Ajouté parce que l'arbitrage (#7) exige l'identifiant d'une demande, que rien n'exposait une
     * fois celle-ci créée : ni endpoint de lecture, ni champ du dossier, ni détail d'historique. Le
     * DJ/DJA, seul habilité à arbitrer, ne pouvait donc pas le faire (QF-23 du frontend).
     * <p>
     * La liste contient aussi la proposition initiale faite à la création du dossier : c'est
     * l'historique complet des seuils demandés, pas seulement celui des dérogations.
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
         * Q-74 : tant que la sensibilité n'est pas validée, le seul audit en attente est la
         * proposition initiale de la création. Elle se tranche par la validation de sensibilité
         * (#8), qui met aussi à jour le statut du dossier. L'arbitrer ici comme une dérogation
         * fixerait un seuil en laissant la sensibilité en attente. Latent tant qu'aucun identifiant
         * n'était exposé, ce contournement devenait atteignable avec la lecture ci-dessus.
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

        // Le sens de la décision devient une donnée : « toutes les dérogations rejetées » se
        // demande, alors qu'il fallait auparavant lire chaque phrase.
        historiqueActionService.enregistrerPour(validateur, dossierId, TypeActionHistorique.DEROGATION_SEUIL_ARBITREE,
                Map.of("decision", request.statut().name(), "auditId", auditId,
                        "motif", Optional.ofNullable(request.motif()).orElse("")));

        return auditSeuilMapper.toResponse(audit);
    }

    // ------------------------------------------------------------------ Validation de la sensibilité

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
                // Le DJ ajuste le type au moment de valider : même référentiel, même contrôle.
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
