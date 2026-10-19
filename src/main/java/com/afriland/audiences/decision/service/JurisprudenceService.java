package com.afriland.audiences.decision.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.decision.domain.Jurisprudence;
import com.afriland.audiences.decision.domain.TypeArchiveJurisprudence;
import com.afriland.audiences.decision.repository.JurisprudenceRepository;
import com.afriland.audiences.decision.web.dto.JurisprudenceResponse;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Case-law base (FR-DEF-03/RG-DEF-05, US 8.3). The case file is optional; files are stored via
 * {@code MinioStorageService} without creating a {@code document} row.
 */
@Service
@RequiredArgsConstructor
public class JurisprudenceService {

    private static final long TAILLE_MAX_OCTETS = 10L * 1024 * 1024;
    private static final String CONTENT_TYPE_PDF = "application/pdf";

    private final JurisprudenceRepository jurisprudenceRepository;
    private final DossierRepository dossierRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;
    private final HistoriqueActionService historiqueActionService;
    private final MinioStorageService minioStorageService;

    /**
     * A decision only enters the case-law base once <b>final</b> (no further appeal): no step of the
     * case file may still be open. {@code Jurisprudence} records nothing about the decision's
     * procedural state, so an appealed first-instance ruling would otherwise look like a final one.
     * A case-law entry without a case file (a precedent found elsewhere) is not checked.
     */
    private void verifierQueLeDossierEstTermine(Dossier dossier) {
        if (etapeProcedureRepository.existsByDossierIdAndStatutCycleVieNotIn(
                dossier.getId(), StatutCycleVie.FERMES)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Ce dossier porte encore une étape en cours : une décision ne rejoint la base "
                            + "jurisprudentielle qu'une fois définitive, tous recours épuisés.");
        }
    }

    /**
     * RG-DEF-05: PDF file up to 10 MB, indexing required, {@code dossierId} optional. Format and size
     * are specific to FR-DEF-03 and have no {@code configuration_systeme} key, hence hard-coded.
     */
    @Transactional
    public JurisprudenceResponse creer(Long dossierId, TypeArchiveJurisprudence typeArchive, List<String> motsCles,
                                        String natureDecision, String juridiction, LocalDate dateDecision, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Le fichier de la jurisprudence est obligatoire");
        }
        if (!CONTENT_TYPE_PDF.equals(file.getContentType())) {
            throw ApiException.badRequest(ErrorCodes.DOCUMENT_NON_CONFORME, "Seul le format PDF est accepté pour une jurisprudence");
        }
        if (file.getSize() > TAILLE_MAX_OCTETS) {
            throw ApiException.badRequest(ErrorCodes.DOCUMENT_NON_CONFORME, "Le fichier dépasse la taille maximale de 10 Mo");
        }
        if (motsCles == null || motsCles.isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Au moins un mot-clé est obligatoire (indexation, RG-DEF-05)");
        }

        Dossier dossier = dossierId != null ? getDossierOrThrow(dossierId) : null;
        if (dossier != null) {
            verifierQueLeDossierEstTermine(dossier);
        }
        String cle = dossier != null ? minioStorageService.deposer(dossierId, file) : minioStorageService.deposerSansDossier(file);

        Jurisprudence jurisprudence = jurisprudenceRepository.save(Jurisprudence.builder()
                .dossier(dossier)
                .typeArchive(typeArchive)
                .motsCles(motsCles)
                .natureDecision(natureDecision)
                .juridiction(juridiction)
                .dateDecision(dateDecision)
                .cheminStockage(cle)
                .build());

        historiqueActionService.enregistrer(dossierId, TypeActionHistorique.JURISPRUDENCE_INDEXEE,
                Map.of("jurisprudenceId", jurisprudence.getId(), "typeArchive", typeArchive.name()));

        return toResponse(jurisprudence);
    }

    /**
     * UC-DEF-03: search by keywords/nature/court, each filter optional. Partial and case-insensitive
     * (a lowercase fragment matches a capitalised keyword), and filterable by case file.
     */
    @Transactional(readOnly = true)
    public PageResponse<JurisprudenceResponse> rechercher(String motCle, String natureDecision, String juridiction,
                                                          Long dossierId, Pageable pageable) {
        Page<Jurisprudence> page = jurisprudenceRepository.rechercher(
                motif(motCle), motif(natureDecision), motif(juridiction), dossierId, pageable);
        return PageResponse.of(page, this::toResponse);
    }

    /**
     * Lowercase "contains" {@code LIKE} pattern; {@code null} for an empty filter. User-typed
     * wildcards ({@code %}, {@code _}) are escaped and matched literally.
     */
    static String motif(String terme) {
        if (terme == null || terme.isBlank()) {
            return null;
        }
        String echappe = terme.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + echappe + "%";
    }

    private JurisprudenceResponse toResponse(Jurisprudence jurisprudence) {
        String url = jurisprudence.getCheminStockage() != null ? minioStorageService.urlTelechargement(jurisprudence.getCheminStockage()) : null;
        return new JurisprudenceResponse(
                jurisprudence.getId(),
                jurisprudence.getDossier() != null ? jurisprudence.getDossier().getId() : null,
                jurisprudence.getTypeArchive(),
                jurisprudence.getMotsCles(),
                jurisprudence.getNatureDecision(),
                jurisprudence.getJuridiction(),
                jurisprudence.getDateDecision(),
                url,
                jurisprudence.getCreatedAt());
    }

    private Dossier getDossierOrThrow(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + id));
    }
}
