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
 * Épic 8, volet jurisprudence (FR-DEF-03/RG-DEF-05, US 8.3) — cf. FUNCTIONAL_MAP.md §8,
 * API_IMPLEMENTATION_STATUS.md §10. Dépend de `dossier` (optionnel, Q-58) et `ged`
 * (`MinioStorageService`, sans création de `document` GED).
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
     * RG-DEF-05 : fichier PDF ≤ 10 Mo, indexation obligatoire, {@code dossierId} optionnel. Ces
     * deux contraintes (format/poids) sont propres à FR-DEF-03 — pas de clé
     * {@code configuration_systeme} dédiée (contrairement à `FORMATS_GED`/`TAILLE_MAX_GED`, absentes
     * de CONF01-07 pour ce workflow), donc littérales plutôt que configurables.
     */
    /**
     * Une décision ne rejoint la base jurisprudentielle que lorsqu'elle est <b>définitive</b>
     * (métier, 2026-09-29).
     *
     * <p><b>Ce que cela protège.</b> {@code Jurisprudence} n'enregistre rien qui dise l'état de la
     * décision : ni statut, ni lien vers l'étape, ni mention « frappée d'appel ». Les trois types
     * d'archive — extrait de minute, expédition, grosse — désignent des formes de l'acte, pas des
     * états de la procédure. Une décision de première instance versée pendant que l'appel court
     * entrait donc dans la base au même titre qu'un arrêt définitif, et le juriste qui la trouvait
     * comme précédent ne pouvait pas savoir qu'elle avait peut-être été infirmée depuis.
     *
     * <p>« Définitive » a un sens précis — qui n'est plus susceptible de recours — et c'est le nom
     * même du module. La garde le fait tenir : plus aucune étape du dossier ne doit être en cours.
     *
     * <p><b>Une jurisprudence sans dossier reste libre</b> : {@code dossierId} est facultatif, et
     * sert justement à verser un arrêt trouvé ailleurs, sans rapport avec un dossier de la banque.
     * Rien ne permet — ni n'a à permettre — d'en juger l'état.
     */
    private void verifierQueLeDossierEstTermine(Dossier dossier) {
        if (etapeProcedureRepository.existsByDossierIdAndStatutCycleVieNotIn(
                dossier.getId(), StatutCycleVie.FERMES)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Ce dossier porte encore une étape en cours : une décision ne rejoint la base "
                            + "jurisprudentielle qu'une fois définitive, tous recours épuisés.");
        }
    }

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
     * UC-DEF-03 : recherche par mots-clés/nature/juridiction, chaque filtre optionnel. Depuis Q-89 :
     * partielle et insensible à la casse (« saisie » retrouve « Saisie immobilière »), et filtrable
     * par dossier — la fiche liste les décisions qu'on y a archivées.
     */
    @Transactional(readOnly = true)
    public PageResponse<JurisprudenceResponse> rechercher(String motCle, String natureDecision, String juridiction,
                                                          Long dossierId, Pageable pageable) {
        Page<Jurisprudence> page = jurisprudenceRepository.rechercher(
                motif(motCle), motif(natureDecision), motif(juridiction), dossierId, pageable);
        return PageResponse.of(page, this::toResponse);
    }

    /**
     * Motif {@code LIKE} « contient », en minuscules ; {@code null} pour un filtre vide. Les jokers
     * saisis par l'utilisateur ({@code %}, {@code _}) sont échappés : ils se cherchent tels quels.
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
