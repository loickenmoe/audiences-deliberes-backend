package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import com.afriland.audiences.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Audit des actions historisées (Q-112) — <b>DJ et DJA</b>.
 *
 * <p>Remplace {@code GET /historique/systeme} (Q-110), qui ne lisait que les actions sans dossier.
 * Celui-ci couvre tout l'historique, le périmètre devenant un critère parmi les autres ; la vue
 * « hors dossier » n'est plus un endpoint mais une valeur de filtre. Le journal livré en Q-110 était
 * un palliatif assumé, taillé étroit pour combler un trou sans préjuger de la suite : une fois la
 * vue générale ouverte, il n'en était plus qu'un sous-ensemble, et deux endroits où chercher la même
 * trace sont exactement la fragmentation qu'on voulait supprimer.
 *
 * <p><b>Pourquoi DJ et DJA, et pas {@code ROLE_CONSULTATION}</b> qui suffit pour l'historique d'un
 * dossier : sur une fiche, on regarde un dossier, et voir l'action d'un collègue relève de la
 * collaboration. Ici la lecture est agrégée et filtrable par auteur — même contenu, autre nature.
 *
 * <p><b>Ce n'est pas un audit complet, et l'écran le dit.</b> Seules les modifications sont
 * historisées ; aucune consultation ne l'est, hormis celle de cet écran. L'absence de ligne ne
 * prouve donc pas l'absence d'action.
 */
@RestController
@RequestMapping("${api.prefix}/historique")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final AuditExportService auditExportService;
    private final ExportAuditRepository exportAuditRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public AuditResponse audit(
            @RequestParam(defaultValue = "TOUT") Perimetre perimetre,
            @RequestParam(required = false) Long dossierId,
            /* L'auteur recherché, autorisé par NF-CONF-01 (2026-09-17). C'est le filtre qui donne
             * sa valeur à l'écran, et celui qui rend nécessaire d'historiser la consultation. */
            @RequestParam(required = false) Long utilisateurId,
            /* Code typé (Q-111) : une valeur inconnue est refusée en 400 par la conversion, là où
             * une phrase libre renvoyait silencieusement zéro ligne. */
            @RequestParam(required = false) TypeActionHistorique code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return auditService.consulter(perimetre, dossierId, utilisateurId, code, dateDebut, dateFin, page, size);
    }

    /**
     * Extraction de l'audit vers un fichier scellé (Q-113).
     *
     * <p><b>La période est obligatoire ici, alors qu'elle est facultative à l'écran.</b> Une archive
     * sans période ne dit pas ce qu'elle contient : son empreinte ne prouverait que son intégrité,
     * jamais son exhaustivité. Consulter tout l'historique a un sens ; en produire une pièce
     * « sans période » n'en a pas.
     *
     * <p><b>Les raccourcis de période — jour, semaine, mois, trimestre, semestre, année — sont
     * calculés par l'écran</b>, et n'apparaissent pas ici. Ce sont des commodités de saisie, pas des
     * concepts d'API : les exposer aurait figé six calendriers côté serveur et interdit la période
     * libre, celle que réclame la première inspection qui demande « du 12 mars au 4 juin ».
     *
     * <p>Le fichier est rendu <b>et</b> déposé : l'appelant repart avec sa pièce, l'archive reste.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(defaultValue = "TOUT") Perimetre perimetre,
            @RequestParam(required = false) Long dossierId,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) TypeActionHistorique code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "PDF") FormatExportAudit format) {
        var extraction = auditExportService.exporter(
                perimetre, dossierId, utilisateurId, code, dateDebut, dateFin, format);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, format.typeMime())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + extraction.nomFichier() + "\"")
                // L'empreinte voyage avec le fichier : le destinataire peut la comparer sans avoir
                // à interroger l'application.
                .header("X-Empreinte-SHA256", extraction.archive().getEmpreinte())
                .body(extraction.contenu());
    }

    /** Les archives produites, de la plus récente à la plus ancienne, avec leur empreinte. */
    @GetMapping("/exports")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public PageResponse<ExportAuditResponse> archives(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return PageResponse.of(
                exportAuditRepository.findAllByOrderByDateExportDescIdDesc(PageRequest.of(page, size)),
                AuditExportService.versReponse());
    }

    /**
     * Récupération d'une archive déjà produite. Une URL pré-signée, valable quelques minutes, et
     * <b>une trace</b> : sans elle, on pourrait puiser dans les archives sans que rien ne le dise.
     */
    @GetMapping("/exports/{id}/telechargement")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public Map<String, String> telecharger(@PathVariable Long id) {
        return Map.of("url", auditExportService.telecharger(id));
    }
}
