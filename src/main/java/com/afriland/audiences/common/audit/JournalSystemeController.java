package com.afriland.audiences.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Journal des actions historisées hors dossier (Q-110) — <b>DJ et DJA</b>.
 *
 * <p><b>Pourquoi ces deux profils et pas {@code ROLE_CONSULTATION}</b>, alors que l'historique d'un
 * dossier est ouvert à tout profil interne. Sur une fiche, voir l'action d'un collègue relève de la
 * collaboration : on regarde un dossier, pas une personne. Ici la lecture est agrégée et filtrable
 * par auteur — le même contenu, sous une forme qui change de nature. La restreindre à
 * l'administration est le minimum tant que {@code NF-CONF-01} (RGPD, conservation) reste « à définir
 * avec la DRH » dans les sources.
 *
 * <p><b>Ce n'est pas un audit du système, et le nom le dit.</b> Seules les modifications sont
 * historisées (RG-DOS-06) ; aucune consultation ne l'est. Baptiser cet écran « Audit » promettrait
 * une exhaustivité qu'il n'a pas, et l'absence de ligne s'y lirait comme l'absence d'action.
 */
@RestController
@RequestMapping("${api.prefix}/historique")
@RequiredArgsConstructor
public class JournalSystemeController {

    private final JournalSystemeService journalSystemeService;

    @GetMapping("/systeme")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public JournalSystemeResponse journalSysteme(
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return journalSystemeService.consulter(utilisateurId, action, dateDebut, dateFin, page, size);
    }
}
