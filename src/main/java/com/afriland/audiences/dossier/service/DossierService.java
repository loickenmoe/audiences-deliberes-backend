package com.afriland.audiences.dossier.service;

import com.afriland.audiences.common.audit.HistoriqueAction;
import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
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
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.dossier.web.dto.AuditSeuilResponse;
import com.afriland.audiences.dossier.web.dto.CreerDossierRequest;
import com.afriland.audiences.dossier.web.dto.CreerEtapeRequest;
import com.afriland.audiences.dossier.web.dto.DemandeDerogationSeuilRequest;
import com.afriland.audiences.dossier.web.dto.DossierResponse;
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
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.web.dto.VueClientDossiersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DossierService {

    private final DossierRepository dossierRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;
    private final DossierJuristeRepository dossierJuristeRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final AuditSeuilDossierRepository auditSeuilDossierRepository;
    private final HistoriqueActionRepository historiqueActionRepository;
    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AvocatRepository avocatRepository;
    private final NatureDossierRepository natureDossierRepository;
    private final EtapeTransitionValidator transitionValidator;
    private final DossierMapper dossierMapper;
    private final EtapeProcedureMapper etapeProcedureMapper;
    private final AuditSeuilMapper auditSeuilMapper;
    private final HistoriqueActionMapper historiqueActionMapper;
    private final HistoriqueActionService historiqueActionService;
    private final CurrentUserService currentUserService;

    // ------------------------------------------------------------------ Création

    @Transactional
    public DossierResponse creerDossier(CreerDossierRequest request) {
        if (dossierRepository.existsByReference(request.reference())) {
            throw ApiException.conflict(ErrorCodes.REFERENCE_DOSSIER_DUPLIQUEE,
                    "La référence '" + request.reference() + "' est déjà utilisée");
        }
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Client introuvable : " + request.clientId()));

        CategorieDossier categorie = resoudreCategorie(request);
        validerChampsConditionnels(request, categorie);

        Dossier dossier = Dossier.builder()
                .reference(request.reference())
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
                        "seuilMontant et typeClientSensible sont obligatoires pour un dossier sensible");
            }
            creerPropositionSeuil(dossier, request.seuilMontant(),
                    "Proposition initiale de sensibilité à la création du dossier");
        }

        Utilisateur auteur = currentUserService.getCurrentUser();
        historiqueActionService.enregistrerPour(auteur, dossier.getId(), "Création du dossier",
                Map.of("reference", dossier.getReference(), "categorie", categorie.name()));

        return construireReponse(dossier, List.of(instance), juristesAffectes, avocatsAffectes, List.of());
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

    private void validerChampsConditionnels(CreerDossierRequest request, CategorieDossier categorie) {
        if (categorie == CategorieDossier.RECOUVREMENT) {
            if (!StringUtils.hasText(request.dossierCreditOrigine()) || !StringUtils.hasText(request.pvTransfert())) {
                throw ApiException.badRequest(ErrorCodes.METADONNEES_MANQUANTES,
                        "dossierCreditOrigine et pvTransfert sont obligatoires pour un dossier RECOUVREMENT");
            }
        } else {
            if (!StringUtils.hasText(request.incidentCompteReference())
                    || !StringUtils.hasText(request.elementsJustificatifs())) {
                throw ApiException.badRequest(ErrorCodes.METADONNEES_MANQUANTES,
                        "incidentCompteReference et elementsJustificatifs sont obligatoires pour un dossier EXPLOITATION_LITIGES");
            }
        }
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
            return avocatId;
        }).toList();
    }

    // ------------------------------------------------------------------ Consultation

    @Transactional(readOnly = true)
    public PageResponse<DossierResponse> listerDossiers(String nature, CategorieDossier categorie,
                                                          String juridiction, Pageable pageable) {
        var page = dossierRepository.findAll(
                DossierSpecifications.withFilters(nature, categorie, juridiction), pageable);
        return PageResponse.of(page, dossier -> construireReponse(dossier,
                etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossier.getId()),
                dossierJuristeRepository.findByDossierId(dossier.getId()).stream()
                        .map(dj -> dj.getUtilisateur().getId()).toList(),
                dossierAvocatRepository.findByDossierId(dossier.getId()).stream()
                        .map(da -> da.getAvocat().getId()).toList(),
                List.of()));
    }

    @Transactional(readOnly = true)
    public DossierResponse consulterDossier(Long id) {
        Dossier dossier = getDossierOrThrow(id);
        List<EtapeProcedure> etapes = etapeProcedureRepository.findByDossierIdOrderByTypeAsc(id);
        List<Long> juristesAffectes = dossierJuristeRepository.findByDossierId(id).stream()
                .map(dj -> dj.getUtilisateur().getId()).toList();
        List<Long> avocatsAffectes = dossierAvocatRepository.findByDossierId(id).stream()
                .map(da -> da.getAvocat().getId()).toList();
        List<HistoriqueAction> historique = historiqueActionRepository
                .findByDossierIdOrderByDateActionDesc(id, Pageable.unpaged()).getContent();
        return construireReponse(dossier, etapes, juristesAffectes, avocatsAffectes, historique);
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
        List<DossierResponse> recouvrement = dossiers.stream()
                .filter(d -> d.getCategorie() == CategorieDossier.RECOUVREMENT)
                .map(this::construireReponseLegere)
                .toList();
        List<DossierResponse> exploitationLitiges = dossiers.stream()
                .filter(d -> d.getCategorie() == CategorieDossier.EXPLOITATION_LITIGES)
                .map(this::construireReponseLegere)
                .toList();
        return new VueClientDossiersResponse(clientId, client.getNom(), recouvrement, exploitationLitiges);
    }

    private DossierResponse construireReponseLegere(Dossier dossier) {
        List<EtapeProcedure> etapes = etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossier.getId());
        List<Long> juristesAffectes = dossierJuristeRepository.findByDossierId(dossier.getId()).stream()
                .map(dj -> dj.getUtilisateur().getId()).toList();
        List<Long> avocatsAffectes = dossierAvocatRepository.findByDossierId(dossier.getId()).stream()
                .map(da -> da.getAvocat().getId()).toList();
        return construireReponse(dossier, etapes, juristesAffectes, avocatsAffectes, List.of());
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
        if ((doublonJuriste || doublonAvocat) && !forcer) {
            throw ApiException.conflict(ErrorCodes.DOUBLON_AFFECTATION,
                    "Au moins un juriste ou avocat est déjà affecté à ce dossier ; ajoutez ?forcer=true pour confirmer (RG-DOS-07)");
        }

        dossierJuristeRepository.deleteByDossierId(dossierId);
        dossierAvocatRepository.deleteByDossierId(dossierId);
        List<Long> juristesAffectes = affecterJuristes(dossier, juristesDemandes);
        List<Long> avocatsAffectes = affecterAvocats(dossier, avocatsDemandes);

        historiqueActionService.enregistrer(dossierId, "Modification de l'affectation",
                Map.of("juristesAffectes", juristesAffectes, "avocatsAffectes", avocatsAffectes));

        List<EtapeProcedure> etapes = etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossierId);
        return construireReponse(dossier, etapes, juristesAffectes, avocatsAffectes, List.of());
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

        etape.setStatutCycleVie(nouveau);
        etape = etapeProcedureRepository.save(etape);

        historiqueActionService.enregistrer(dossierId, "Changement de statut de l'étape " + etape.getType(),
                Map.of("ancienStatut", ancien.name(), "nouveauStatut", nouveau.name()));

        if (transitionValidator.estExerciceDeRecours(ancien, nouveau)) {
            ouvrirEtapeSuivante(dossier, etape);
        }

        return etapeProcedureMapper.toResponse(etape);
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
        historiqueActionService.enregistrer(dossier.getId(),
                "Ouverture automatique de l'étape " + suivante + " (exercice d'un recours)",
                Map.of("etapeId", nouvelle.getId()));
    }

    @Transactional
    public EtapeProcedureResponse creerEtape(Long dossierId, CreerEtapeRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        if (etapeProcedureRepository.existsByDossierIdAndType(dossierId, request.type())) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "L'étape " + request.type() + " existe déjà pour ce dossier");
        }
        // Positionnement direct (UC-DOS-04 alt. 3-b) : crée les étapes intermédiaires manquantes
        // au statut OUVERTURE, sans historique détaillé pour celles-ci (RG-DOS-04).
        for (TypeEtape intermediaire : etapesPrecedentes(request.type())) {
            if (!etapeProcedureRepository.existsByDossierIdAndType(dossierId, intermediaire)) {
                creerEtapeInterne(dossier, intermediaire, StatutCycleVie.OUVERTURE);
            }
        }
        EtapeProcedure etape = creerEtapeInterne(dossier, request.type(), StatutCycleVie.OUVERTURE);
        historiqueActionService.enregistrer(dossierId,
                "Positionnement direct à l'étape " + request.type() + " (antécédents inconnus)",
                Map.of("etapeId", etape.getId()));
        return etapeProcedureMapper.toResponse(etape);
    }

    private List<TypeEtape> etapesPrecedentes(TypeEtape type) {
        return switch (type) {
            case INSTANCE -> List.of();
            case RECOURS_1 -> List.of(TypeEtape.INSTANCE);
            case RECOURS_2 -> List.of(TypeEtape.INSTANCE, TypeEtape.RECOURS_1);
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
                    "Une dérogation de seuil ne peut être demandée que sur un dossier sensible déjà validé");
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
        historiqueActionService.enregistrerPour(auteur, dossier.getId(), "Demande de dérogation de seuil",
                Map.of("nouveauSeuil", nouveauSeuil, "motif", motif));
        return audit;
    }

    @Transactional
    public AuditSeuilResponse validerDerogationSeuil(Long dossierId, Long auditId, ValiderDerogationRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
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

        historiqueActionService.enregistrerPour(validateur, dossierId,
                "Décision sur la dérogation de seuil : " + request.statut(),
                Map.of("auditId", auditId, "motif", Optional.ofNullable(request.motif()).orElse("")));

        return auditSeuilMapper.toResponse(audit);
    }

    // ------------------------------------------------------------------ Validation de la sensibilité

    @Transactional
    public DossierResponse validerSensibilite(Long dossierId, ValiderSensibiliteRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        if (!Boolean.TRUE.equals(dossier.getEstSensible()) || dossier.getSensibiliteStatut() != StatutValidation.EN_ATTENTE) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Aucune proposition de sensibilité en attente pour ce dossier");
        }
        if (request.statut() == StatutValidation.REJETEE && !StringUtils.hasText(request.motifRejet())) {
            throw ApiException.badRequest(ErrorCodes.METADONNEES_MANQUANTES, "Le motif est obligatoire en cas de rejet");
        }

        Utilisateur validateur = currentUserService.getCurrentUser();
        dossier.setSensibiliteStatut(request.statut());
        dossier.setSensibiliteValidePar(validateur);

        if (request.statut() == StatutValidation.VALIDEE) {
            if (request.typeClientSensibleAjuste() != null) {
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

        historiqueActionService.enregistrerPour(validateur, dossierId,
                "Décision sur la sensibilité du dossier : " + request.statut(),
                Map.of("motifRejet", Optional.ofNullable(request.motifRejet()).orElse("")));

        List<EtapeProcedure> etapes = etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossierId);
        List<Long> juristesAffectes = dossierJuristeRepository.findByDossierId(dossierId).stream()
                .map(dj -> dj.getUtilisateur().getId()).toList();
        List<Long> avocatsAffectes = dossierAvocatRepository.findByDossierId(dossierId).stream()
                .map(da -> da.getAvocat().getId()).toList();
        return construireReponse(dossier, etapes, juristesAffectes, avocatsAffectes, List.of());
    }
}
