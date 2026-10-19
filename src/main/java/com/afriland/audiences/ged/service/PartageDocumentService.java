package com.afriland.audiences.ged.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.ged.domain.DecisionPartage;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.StatutPartageAvocat;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.web.dto.ArbitragePartageRequest;
import com.afriland.audiences.ged.web.dto.EtatPartageDossierResponse;
import com.afriland.audiences.ged.web.dto.PartageAvocatResponse;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sharing a GED document with the case file's lawyer.
 *
 * <p><b>Rules.</b>
 * <ol>
 *   <li>Any legal officer can share a document without DJ approval, <b>including the SH and the
 *       Assistant</b>, who are treated as legal officers.</li>
 *   <li>On a <b>sensitive</b> case file, sharing is reserved to the DJ and DJA: a legal officer, SH
 *       or Assistant only <b>proposes</b> it, and nothing is visible to the lawyer before approval.
 *       A DJ or DJA who shares directly does not need to approve their own share. The rule is read
 *       from the profile ({@link #DIRECTION}).</li>
 *   <li>A lawyer only sees a shared document if assigned to the case file <b>and appointed</b>:
 *       {@code PortailAvocatService} checks this on every read.</li>
 * </ol>
 *
 * <p><b>A sensitive case file is read from {@code estSensible}</b>, including a pending sensitivity
 * proposal (a rejection resets it to false). When in doubt the DJ approves: this is the cautious
 * side, since a wrongly shared document cannot be taken back.
 *
 * <p>Kept separate from {@code GedService}, which already has many dependencies: sharing is a
 * workflow distinct from upload and deletion.
 */
@Service
@RequiredArgsConstructor
public class PartageDocumentService {

    /** Profiles that decide on a sensitive case file. Read from the profile, never from the role. */
    private static final Set<String> DIRECTION = Set.of("DJ", "DJA");

    private final DocumentRepository documentRepository;
    private final CurrentUserService currentUserService;
    private final HistoriqueActionService historiqueActionService;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final DemandeConstitutionRepository demandeConstitutionRepository;

    /**
     * Is a lawyer assigned to the case file also appointed on it?
     *
     * <p>The same rule that opens documents to the lawyer, evaluated for the person sharing: without
     * it, they would see "Shared" while the lawyer sees nothing. A case file has few lawyers, so one
     * read per assigned lawyer is negligible.
     */
    @Transactional(readOnly = true)
    public EtatPartageDossierResponse etat(Long dossierId) {
        boolean constitue = dossierAvocatRepository.findByDossierId(dossierId).stream()
                .anyMatch(affectation -> demandeConstitutionRepository.existsByDossierIdAndPrestataireIdAndStatut(
                        dossierId, affectation.getAvocat().getId(), StatutConstitution.VALIDEE));
        return new EtatPartageDossierResponse(constitue);
    }

    /**
     * Shares a document or, on a sensitive case file, proposes sharing it.
     *
     * @throws ApiException 409 if the document is already shared or awaiting approval
     */
    @Transactional
    public PartageAvocatResponse partager(Long documentId) {
        Document document = documentOuErreur(documentId);
        if (document.getPartageAvocat() != StatutPartageAvocat.NON) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "Cette pièce est déjà partagée, ou son partage attend la validation du DJ ou de la DJA");
        }
        Utilisateur auteur = currentUserService.getCurrentUser();
        boolean sensible = Boolean.TRUE.equals(document.getDossier().getEstSensible());
        boolean direction = estDirection(auteur);
        LocalDateTime maintenant = LocalDateTime.now();

        document.setPartagePar(auteur);
        document.setPartageLe(maintenant);
        TypeActionHistorique trace;
        if (sensible && !direction) {
            document.setPartageAvocat(StatutPartageAvocat.PROPOSE);
            trace = TypeActionHistorique.DOCUMENT_PARTAGE_PROPOSE;
        } else {
            document.setPartageAvocat(StatutPartageAvocat.PARTAGE);
            if (sensible) {
                // A DJ or DJA who shares approves in the same action: their decision is the approval.
                document.setPartageValidePar(auteur);
                document.setPartageValideLe(maintenant);
            }
            trace = TypeActionHistorique.DOCUMENT_PARTAGE_AVOCAT;
        }
        document = documentRepository.save(document);
        historiqueActionService.enregistrer(document.getDossier().getId(), trace, Map.of("documentId", document.getId()));
        return versReponse(document);
    }

    /**
     * Withdraws a share or cancels a proposal. Open to any legal officer on any case file:
     * withdrawing reduces exposure and therefore needs no approval.
     *
     * @throws ApiException 409 if the document is neither shared nor proposed
     */
    @Transactional
    public PartageAvocatResponse retirer(Long documentId) {
        Document document = documentOuErreur(documentId);
        StatutPartageAvocat ancien = document.getPartageAvocat();
        if (ancien == StatutPartageAvocat.NON) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Cette pièce n'est pas partagée");
        }
        reinitialiser(document);
        document = documentRepository.save(document);
        historiqueActionService.enregistrer(document.getDossier().getId(), TypeActionHistorique.DOCUMENT_PARTAGE_RETIRE,
                Map.of("documentId", document.getId(), "ancienStatut", ancien.name()));
        return versReponse(document);
    }

    /**
     * The DJ or DJA decides on a share proposed on a sensitive case file.
     *
     * @throws ApiException 409 if no share is awaiting approval; 400 on a rejection without a reason
     */
    @Transactional
    public PartageAvocatResponse arbitrer(Long documentId, ArbitragePartageRequest request) {
        Document document = documentOuErreur(documentId);
        if (document.getPartageAvocat() != StatutPartageAvocat.PROPOSE) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Ce partage n'attend aucune validation");
        }
        String motif = request.motif() == null ? null : request.motif().trim();
        if (request.decision() == DecisionPartage.REJETE && (motif == null || motif.isEmpty())) {
            // The legal officer who proposed must know why the document is not shared.
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Le rejet d'un partage exige un motif");
        }

        if (request.decision() == DecisionPartage.VALIDE) {
            document.setPartageAvocat(StatutPartageAvocat.PARTAGE);
            document.setPartageValidePar(currentUserService.getCurrentUser());
            document.setPartageValideLe(LocalDateTime.now());
        } else {
            reinitialiser(document);
        }
        document = documentRepository.save(document);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("documentId", document.getId());
        details.put("decision", request.decision().name());
        if (request.decision() == DecisionPartage.REJETE) {
            details.put("motif", motif);
        }
        historiqueActionService.enregistrer(document.getDossier().getId(), TypeActionHistorique.DOCUMENT_PARTAGE_ARBITRE, details);
        return versReponse(document);
    }

    /** The DJ/DJA queue: proposed shares, oldest first. */
    @Transactional(readOnly = true)
    public PageResponse<PartageAvocatResponse> propositions(Pageable pageable) {
        return PageResponse.of(documentRepository.findByPartageAvocatAndEstSupprimeFalseOrderByPartageLeAsc(
                StatutPartageAvocat.PROPOSE, pageable), this::versReponse);
    }

    /** A deleted document can no longer be shared: it no longer exists for anyone outside the audit. */
    private Document documentOuErreur(Long documentId) {
        return documentRepository.findById(documentId)
                .filter(document -> !Boolean.TRUE.equals(document.getEstSupprime()))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Document introuvable : " + documentId));
    }

    private static void reinitialiser(Document document) {
        document.setPartageAvocat(StatutPartageAvocat.NON);
        document.setPartagePar(null);
        document.setPartageLe(null);
        document.setPartageValidePar(null);
        document.setPartageValideLe(null);
    }

    private static boolean estDirection(Utilisateur utilisateur) {
        return utilisateur != null && utilisateur.getProfil() != null
                && DIRECTION.contains(utilisateur.getProfil().getCode());
    }

    private PartageAvocatResponse versReponse(Document document) {
        return new PartageAvocatResponse(document.getId(), document.getDossier().getId(),
                document.getDossier().getReference(), document.getNomFichier(), document.getTypeDocument(),
                document.getPartageAvocat(), nomComplet(document.getPartagePar()), document.getPartageLe());
    }

    private static String nomComplet(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        return Stream.of(utilisateur.getPrenom(), utilisateur.getNom())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }
}
