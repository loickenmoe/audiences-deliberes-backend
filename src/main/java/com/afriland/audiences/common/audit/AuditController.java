package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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
}
