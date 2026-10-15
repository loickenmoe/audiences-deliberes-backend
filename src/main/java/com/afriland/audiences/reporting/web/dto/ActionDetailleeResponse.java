package com.afriland.audiences.reporting.web.dto;

import com.afriland.audiences.common.audit.TypeActionHistorique;

import java.time.LocalDateTime;

/**
 * Une action derrière un décompte de la fiche d'activité (Q-133) : de quoi ouvrir ce qu'elle a
 * touché.
 *
 * <p><b>Ce que cette réponse rend possible.</b> Un décompte à quatre ne mène nulle part : il faut
 * les quatre actions, et pour chacune le dossier concerné. C'est la colonne {@code dossier_id} de
 * l'historique qui le permet - sans elle, il faudrait relire le {@code jsonb} des détails, dont la
 * clé change selon la nature de l'action.
 *
 * @param dossierId        {@code null} pour une action hors dossier - une modification de
 *                         configuration, une correction du référentiel des clients. La ligne reste
 *                         affichée, sans lien : la masquer ferait disparaître de l'activité réelle,
 *                         et le décompte ne tomberait plus sur le nombre de lignes.
 * @param referenceDossier la référence lisible du dossier, pour que la ligne se lise sans cliquer.
 *                         {@code null} si le dossier a été supprimé depuis.
 */
public record ActionDetailleeResponse(
        Long id,
        TypeActionHistorique code,
        String action,
        LocalDateTime dateAction,
        Long dossierId,
        String referenceDossier) {
}
