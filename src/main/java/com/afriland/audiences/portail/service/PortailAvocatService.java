package com.afriland.audiences.portail.service;

import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.ged.service.ValidationFichierService;
import com.afriland.audiences.intervenant.mapper.DemandeConstitutionMapper;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import com.afriland.audiences.portail.web.dto.AudienceAvocatResponse;
import com.afriland.audiences.portail.web.dto.DossierAvocatResponse;
import com.afriland.audiences.portail.web.dto.PieceAvocatResponse;
import com.afriland.audiences.portail.web.dto.PieceJustificativeResponse;
import com.afriland.audiences.portail.web.dto.PieceTelechargementResponse;
import com.afriland.audiences.portail.web.dto.PiecesDossierAvocatResponse;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.StatutPartageAvocat;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.publication.mapper.PublicationMapper;
import com.afriland.audiences.publication.domain.Publication;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.repository.CorrespondanceAvocatRepository;
import com.afriland.audiences.publication.repository.PublicationRepository;
import com.afriland.audiences.publication.web.dto.CorrespondanceResponse;
import com.afriland.audiences.publication.web.dto.PublicationResponse;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lawyer portal reads. The lawyer lacks {@code ROLE_CONSULTATION}, yet US 4.1 and US 5.1 require
 * choosing a case file and a hearing; the first reads below provide that. The lawyer's own
 * publications and engagement letters let them review what was rejected.
 * <p>
 * Each read is restricted <b>in the query</b>, never by an after-the-fact filter: a forgotten filter
 * would expose the bank's whole portfolio to an external provider. Responses carry only what is needed.
 */
@Service
@RequiredArgsConstructor
public class PortailAvocatService {

    private final CurrentUserService currentUserService;
    private final CompteAvocatService compteAvocatService;
    private final DossierRepository dossierRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final AudienceRepository audienceRepository;
    private final PublicationRepository publicationRepository;
    private final CorrespondanceAvocatRepository correspondanceAvocatRepository;
    private final PublicationMapper publicationMapper;
    private final DemandeConstitutionRepository demandeConstitutionRepository;
    private final DemandeConstitutionMapper demandeConstitutionMapper;
    private final MinioStorageService minioStorageService;
    private final ValidationFichierService validationFichierService;
    private final DocumentRepository documentRepository;
    private final HistoriqueActionService historiqueActionService;

    /** US 4.1, US 5.1: enough to identify the lawyer's case files, nothing more. */
    @Transactional(readOnly = true)
    public PageResponse<DossierAvocatResponse> mesDossiers(Pageable pageable) {
        return mesDossiers(null, pageable);
    }

    /**
     * @param reference reference fragment to search, or {@code null} for the whole list. Searched
     *                  server-side: filtering a loaded page would miss older case files
     */
    @Transactional(readOnly = true)
    public PageResponse<DossierAvocatResponse> mesDossiers(String reference, Pageable pageable) {
        Avocat avocat = avocatCourant();
        Page<Dossier> dossiers = reference == null || reference.isBlank()
                ? dossierRepository.findDossiersDeLAvocat(avocat.getId(), pageable)
                : dossierRepository.rechercherDossiersDeLAvocat(avocat.getId(), reference.strip(), pageable);
        return PageResponse.of(dossiers,
                dossier -> new DossierAvocatResponse(dossier.getId(), dossier.getReference(),
                        dossier.getNature(), dossier.getJuridictionSaisie()));
    }

    /**
     * US 5.1: hearings of one of the lawyer's <b>own</b> case files. An unassigned case file returns 403,
     * not 404: the file exists, access is denied.
     */
    @Transactional(readOnly = true)
    public List<AudienceAvocatResponse> audiencesDeMonDossier(Long dossierId) {
        Avocat avocat = avocatCourant();
        verifierAffectation(dossierId, avocat);
        // The note is only shown to an engaged lawyer: the mandate, not the assignment, opens
        // communication. Read once for the whole list.
        boolean constitue = estConstitue(dossierId, avocat);
        // Deliberately unpaged: this list feeds the hearing selector of the filing form, not a table;
        // paging would silently truncate the lawyer's choice.
        return audienceRepository.findByDossierIdOrderByDatePlanifieeDesc(dossierId, Pageable.unpaged()).stream()
                .map(audience -> new AudienceAvocatResponse(audience.getId(), audience.getDatePlanifiee(),
                        audience.getDateTenue(), audience.getJuridiction(), audience.getStatut(),
                        constitue ? audience.getNoteAvocat() : null))
                .toList();
    }

    /**
     * Case-file documents the bank shares with the lawyer. Not assigned: 403. Assigned but not engaged:
     * empty list with {@code constitue = false}, so the screen can say so rather than "no document".
     * The restriction is in the query, so a change of lawyer revokes access immediately.
     */
    @Transactional(readOnly = true)
    public PiecesDossierAvocatResponse piecesDeMonDossier(Long dossierId) {
        Avocat avocat = avocatCourant();
        verifierAffectation(dossierId, avocat);
        if (!estConstitue(dossierId, avocat)) {
            return new PiecesDossierAvocatResponse(false, List.of());
        }
        List<PieceAvocatResponse> pieces = documentRepository
                .findByDossierIdAndPartageAvocatAndEstSupprimeFalseOrderByDateChargementDesc(
                        dossierId, StatutPartageAvocat.PARTAGE)
                .stream()
                .map(document -> new PieceAvocatResponse(document.getId(), document.getNomFichier(),
                        document.getTypeDocument(), document.getFormat(), document.getPoids(),
                        document.getPartageLe()))
                .toList();
        return new PiecesDossierAvocatResponse(true, pieces);
    }

    /**
     * Opens a shared document: returns the pre-signed URL and records the access.
     * <p>
     * <b>404, not 403</b>, for an unshared document or one from another case file: 403 would confirm it
     * exists and let the lawyer probe the document store by guessing ids. The access is recorded to
     * know whether the lawyer saw the document before the hearing.
     */
    @Transactional
    public PieceTelechargementResponse pieceDeMonDossier(Long dossierId, Long documentId) {
        Avocat avocat = avocatCourant();
        verifierAffectation(dossierId, avocat);
        if (!estConstitue(dossierId, avocat)) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Les pièces du dossier vous seront accessibles une fois votre constitution signée");
        }
        Document document = documentRepository
                .findByIdAndDossierIdAndPartageAvocatAndEstSupprimeFalse(documentId, dossierId, StatutPartageAvocat.PARTAGE)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Pièce introuvable : " + documentId));
        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.DOCUMENT_CONSULTE_PAR_AVOCAT,
                Map.of("documentId", document.getId(), "avocatId", avocat.getId()));
        return new PieceTelechargementResponse(document.getId(), document.getNomFichier(),
                minioStorageService.urlTelechargement(document.getCheminStockage()));
    }

    /** Assigned to the case file, otherwise 403: the file exists, access is denied. */
    private void verifierAffectation(Long dossierId, Avocat avocat) {
        if (!dossierAvocatRepository.existsByDossierIdAndAvocatId(dossierId, avocat.getId())) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Vous n'êtes pas affecté à ce dossier");
        }
    }

    /**
     * Engaged on this case file: an engagement letter <b>signed</b> by the SH. Assignment alone is not
     * a mandate.
     */
    private boolean estConstitue(Long dossierId, Avocat avocat) {
        return demandeConstitutionRepository.existsByDossierIdAndPrestataireIdAndStatut(
                dossierId, avocat.getId(), StatutConstitution.VALIDEE);
    }

    /**
     * UC-INT-01 step 4: the lawyer uploads supporting documents. The file is stored under a dedicated
     * prefix and its <b>key</b> is returned, to be passed in {@code piecesJustificatives} when filing
     * the request. This keeps {@code POST /frais/demandes} as JSON and avoids re-uploading after a
     * failed submission. Same format and size checks as the document store ({@code FORMATS_GED},
     * {@code TAILLE_MAX_GED}).
     */
    @Transactional(readOnly = true)
    public PieceJustificativeResponse deposerPieceJustificative(MultipartFile fichier) {
        avocatCourant();
        validationFichierService.valider(fichier);
        String cle = minioStorageService.deposerPieceFrais(fichier);
        return new PieceJustificativeResponse(cle, fichier.getOriginalFilename(),
                minioStorageService.urlTelechargement(cle));
    }

    /** The lawyer's own submissions, with their status and any rejection reason. */
    @Transactional(readOnly = true)
    public PageResponse<PublicationResponse> mesPublications(StatutPublication statut, Pageable pageable) {
        Avocat avocat = avocatCourant();
        var page = statut == null
                ? publicationRepository.findByAvocatId(avocat.getId(), pageable)
                : publicationRepository.findByAvocatIdAndStatutValidation(avocat.getId(), statut, pageable);

        /*
         * Each publication comes with its correspondence. Comments are deliberately excluded: the
         * legal officer never addresses the lawyer directly (US 5.5, RG-INT-07); comments are for the
         * DJ/DJA, who forward a single correspondence.
         */
        Map<Long, CorrespondanceResponse> correspondances = correspondancesDe(page.getContent().stream()
                .map(Publication::getId)
                .toList());

        return PageResponse.of(page, publication -> publicationMapper.toResponse(
                publication, null, null, correspondances.get(publication.getId())));
    }

    /** Correspondences of a page in a single read; empty if the page is. */
    private Map<Long, CorrespondanceResponse> correspondancesDe(List<Long> publicationIds) {
        if (publicationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, CorrespondanceResponse> parPublication = new HashMap<>();
        for (var correspondance : correspondanceAvocatRepository.findByPublicationIdIn(publicationIds)) {
            Long publicationId = correspondance.getPublication().getId();
            parPublication.put(publicationId, PublicationMapper.versReponse(publicationId, correspondance));
        }
        return parPublication;
    }

    /** Engagement letters naming the lawyer. */
    @Transactional(readOnly = true)
    public List<DemandeConstitutionResponse> mesConstitutions() {
        Avocat avocat = avocatCourant();
        return demandeConstitutionRepository.findByPrestataireIdOrderByDateDemandeDesc(avocat.getId()).stream()
                .map(demandeConstitutionMapper::toResponse)
                .toList();
    }

    /**
     * The lawyer record linked to the logged-in account. A {@code ROLE_AVOCAT} account without a
     * matching record is a setup error: an explicit 403 beats an empty list read as "no case files".
     */
    private Avocat avocatCourant() {
        return compteAvocatService.avocatDe(currentUserService.getCurrentUser())
                .orElseThrow(() -> ApiException.forbidden(ErrorCodes.AVOCAT_NON_RATTACHE,
                        "Aucun avocat du référentiel n'est rattaché à ce compte"));
    }
}
