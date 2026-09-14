package com.afriland.audiences.ged.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.domain.DemandeSuppression;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.FormatDocument;
import com.afriland.audiences.ged.domain.StatutSuppression;
import com.afriland.audiences.ged.mapper.DemandeSuppressionMapper;
import com.afriland.audiences.ged.repository.DemandeSuppressionRepository;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.web.dto.ActePlanifieResponse;
import com.afriland.audiences.ged.web.dto.ApprobationSuppressionRequest;
import com.afriland.audiences.ged.web.dto.DemandeSuppressionResponse;
import com.afriland.audiences.ged.web.dto.DocumentResponse;
import com.afriland.audiences.ged.web.dto.RapportJournalierResponse;
import com.afriland.audiences.ged.web.dto.SuppressionResult;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Épic 6 (RG-GED-02/04/05/06/08/09) — cf. FUNCTIONAL_MAP.md §6, API_IMPLEMENTATION_STATUS.md §8. */
@Service
@RequiredArgsConstructor
public class GedService {

    private final DocumentRepository documentRepository;
    private final DemandeSuppressionRepository demandeSuppressionRepository;
    private final DossierRepository dossierRepository;
    private final DossierJuristeRepository dossierJuristeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final HistoriqueActionRepository historiqueActionRepository;
    private final AudienceRepository audienceRepository;
    private final ConfigurationService configurationService;
    private final AlerteService alerteService;
    private final HistoriqueActionService historiqueActionService;
    private final CurrentUserService currentUserService;
    private final MinioStorageService minioStorageService;
    // RG-GED-04/08, partagé avec les pièces justificatives de frais (Q-97).
    private final ValidationFichierService validationFichierService;
    private final DemandeSuppressionMapper demandeSuppressionMapper;

    @Transactional
    public DocumentResponse uploaderDocument(Long dossierId, String typeDocument, MultipartFile fichier) {
        Dossier dossier = getDossierOrThrow(dossierId);
        FormatDocument format = validationFichierService.valider(fichier);
        Utilisateur auteur = currentUserService.getCurrentUser();

        String cheminStockage = minioStorageService.deposer(dossierId, fichier);
        Document document = documentRepository.save(Document.builder()
                .dossier(dossier)
                .nomFichier(fichier.getOriginalFilename())
                .typeDocument(typeDocument)
                .format(format)
                .poids(fichier.getSize())
                .auteurChargement(auteur)
                .estSupprime(false)
                .cheminStockage(cheminStockage)
                .build());

        historiqueActionService.enregistrerPour(auteur, dossierId, "Chargement d'un document",
                Map.of("documentId", document.getId(), "nomFichier", document.getNomFichier()));

        signalerDocumentCharge(dossier, document);

        return toResponse(document, null);
    }

    /** RG-GED-02 : notifie chaque juriste coté au dossier. */
    private void signalerDocumentCharge(Dossier dossier, Document document) {
        String declencheur = "Nouveau document « " + document.getNomFichier() + " » chargé sur le dossier "
                + dossier.getReference() + ".";
        dossierJuristeRepository.findByDossierId(dossier.getId()).forEach(affectation ->
                alerteService.declencher(TypeAlerte.DOCUMENT_CHARGE, dossier.getId(), affectation.getUtilisateur(), declencheur));
    }

    @Transactional(readOnly = true)
    public DocumentResponse obtenirDocument(Long documentId) {
        Document document = getDocumentActifOrThrow(documentId);
        String url = minioStorageService.urlTelechargement(document.getCheminStockage());
        return toResponse(document, url);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listerDocumentsDossier(Long dossierId) {
        getDossierOrThrow(dossierId);
        return documentRepository.findByDossierIdAndEstSupprimeFalse(dossierId).stream()
                .map(d -> toResponse(d, null))
                .toList();
    }

    /**
     * RG-GED-05/09 : double comportement selon le rôle de l'appelant — juriste auteur du
     * chargement (crée une demande, 202) ou DJ/DJA (suppression immédiate, 200).
     */
    @Transactional
    public SuppressionResult supprimerOuDemander(Long documentId) {
        Document document = getDocumentActifOrThrow(documentId);
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        String profil = utilisateur.getProfil().getCode();

        if ("DJ".equals(profil) || "DJA".equals(profil)) {
            return new SuppressionResult(true, supprimerDirectement(document, utilisateur), null);
        }

        if (!document.getAuteurChargement().getId().equals(utilisateur.getId())) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Seul l'auteur du chargement peut demander la suppression de ce document");
        }
        if (demandeSuppressionRepository.existsByDocumentIdAndStatut(documentId, StatutSuppression.EN_ATTENTE)) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Une demande de suppression est déjà en attente pour ce document");
        }

        DemandeSuppression demande = demandeSuppressionRepository.save(DemandeSuppression.builder()
                .document(document)
                .demandeur(utilisateur)
                .statut(StatutSuppression.EN_ATTENTE)
                .build());

        historiqueActionService.enregistrerPour(utilisateur, document.getDossier().getId(),
                "Demande de suppression d'un document", Map.of("documentId", documentId, "demandeId", demande.getId()));

        signalerDemandeSuppression(document, "Une demande de suppression a été soumise pour le document « "
                + document.getNomFichier() + "» (dossier " + document.getDossier().getReference() + ").");

        return new SuppressionResult(false, null, demandeSuppressionMapper.toResponse(demande));
    }

    private DocumentResponse supprimerDirectement(Document document, Utilisateur validePar) {
        document.setEstSupprime(true);
        documentRepository.save(document);
        minioStorageService.supprimer(document.getCheminStockage());

        // Q-79 : la demande en attente sur ce document est close avec lui. Laissée ouverte, elle
        // restait dans la file, indécidable (document supprimé → 404) et relancée toutes les 72 h.
        demandeSuppressionRepository.findByDocumentIdAndStatut(document.getId(), StatutSuppression.EN_ATTENTE)
                .ifPresent(demande -> {
                    demande.setStatut(StatutSuppression.APPROUVEE);
                    demande.setValidePar(validePar);
                    demande.setDateDecision(LocalDateTime.now());
                    demandeSuppressionRepository.save(demande);
                });

        historiqueActionService.enregistrerPour(validePar, document.getDossier().getId(),
                "Suppression directe d'un document (DJ/DJA)", Map.of("documentId", document.getId()));

        alerteService.declencher(TypeAlerte.SUPPRESSION_DOCUMENT, document.getDossier().getId(), document.getAuteurChargement(),
                "Votre document « " + document.getNomFichier() + " » a été supprimé directement par "
                        + validePar.getProfil().getCode() + ".");

        return toResponse(document, null);
    }

    /**
     * M15 (Q-68) : file de travail « demandes de suppression en attente » du DJ/DJA — endpoint 🆕.
     * Sans filtre, retourne l'historique complet (plus récentes d'abord).
     */
    @Transactional(readOnly = true)
    public List<DemandeSuppressionResponse> listerDemandesSuppression(StatutSuppression statut) {
        var demandes = statut == null
                ? demandeSuppressionRepository.findByOrderByDateDemandeDesc()
                : demandeSuppressionRepository.findByStatutOrderByDateDemandeAsc(statut);
        return demandes.stream().map(demandeSuppressionMapper::toResponse).toList();
    }

    @Transactional
    public DemandeSuppressionResponse approuverOuRejeter(Long documentId, ApprobationSuppressionRequest request) {
        Document document = getDocumentActifOrThrow(documentId);
        DemandeSuppression demande = demandeSuppressionRepository.findByDocumentIdAndStatut(documentId, StatutSuppression.EN_ATTENTE)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Aucune demande de suppression en attente pour ce document"));

        Utilisateur validateur = currentUserService.getCurrentUser();
        demande.setValidePar(validateur);
        demande.setDateDecision(LocalDateTime.now());

        if (request.decision() == StatutSuppression.APPROUVEE) {
            document.setEstSupprime(true);
            documentRepository.save(document);
            minioStorageService.supprimer(document.getCheminStockage());
            demande.setStatut(StatutSuppression.APPROUVEE);
            alerteService.declencher(TypeAlerte.SUPPRESSION_DOCUMENT, document.getDossier().getId(), demande.getDemandeur(),
                    "Votre demande de suppression du document « " + document.getNomFichier() + " » a été approuvée.");
        } else {
            if (!StringUtils.hasText(request.motifRejet())) {
                throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Le motif de rejet est obligatoire");
            }
            demande.setStatut(StatutSuppression.REJETEE);
            demande.setMotifRejet(request.motifRejet());
            alerteService.declencher(TypeAlerte.SUPPRESSION_DOCUMENT, document.getDossier().getId(), demande.getDemandeur(),
                    "Votre demande de suppression du document « " + document.getNomFichier()
                            + " » a été rejetée : " + request.motifRejet());
        }
        demande = demandeSuppressionRepository.save(demande);

        historiqueActionService.enregistrerPour(validateur, document.getDossier().getId(),
                "Décision sur une demande de suppression de document",
                Map.of("documentId", documentId, "decision", request.decision().name()));

        return demandeSuppressionMapper.toResponse(demande);
    }

    private void signalerDemandeSuppression(Document document, String declencheur) {
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue("DJ");
        destinataires.addAll(utilisateurRepository.findByProfilCodeAndActifTrue("DJA"));
        destinataires.forEach(u -> alerteService.declencher(TypeAlerte.SUPPRESSION_DOCUMENT, document.getDossier().getId(), u, declencheur));
    }

    @Transactional(readOnly = true)
    public RapportJournalierResponse rapportJournalier(LocalDate date) {
        LocalDateTime debut = date.atStartOfDay();
        LocalDateTime fin = date.plusDays(1).atStartOfDay();

        long dossiersManipules = historiqueActionRepository.countDistinctDossierIdByDateActionBetween(debut, fin);
        long piecesAjoutees = documentRepository.countByDateChargementBetween(debut, fin);
        long affairesNouvelles = dossierRepository.countByCreatedAtBetween(debut, fin);
        List<ActePlanifieResponse> planificationActes = audienceRepository.findByStatutAndDatePlanifiee(StatutAudience.PLANIFIEE, date)
                .stream()
                .map(this::toActePlanifie)
                .toList();

        return new RapportJournalierResponse(date, dossiersManipules, piecesAjoutees, affairesNouvelles, planificationActes);
    }

    private ActePlanifieResponse toActePlanifie(Audience audience) {
        Dossier dossier = audience.getDossier();
        return new ActePlanifieResponse(dossier.getId(), dossier.getReference(), audience.getDatePlanifiee());
    }

    private Document getDocumentActifOrThrow(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Document introuvable : " + id));
        if (Boolean.TRUE.equals(document.getEstSupprime())) {
            throw ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Document introuvable : " + id);
        }
        return document;
    }

    private Dossier getDossierOrThrow(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + id));
    }

    private DocumentResponse toResponse(Document d, String url) {
        return new DocumentResponse(d.getId(), d.getDossier().getId(), d.getNomFichier(), d.getTypeDocument(),
                d.getFormat(), d.getPoids(), d.getAuteurChargement().getId(), d.getDateChargement(),
                Boolean.TRUE.equals(d.getEstSupprime()), url);
    }
}
