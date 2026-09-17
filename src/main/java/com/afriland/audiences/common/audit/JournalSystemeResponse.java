package com.afriland.audiences.common.audit;

import com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse;

import java.util.List;

/**
 * Journal des actions historisées hors dossier (Q-110).
 *
 * <p>Même forme que {@code PageResponse} — {@code content}, {@code totalPages},
 * {@code totalElements} — plus {@code actionsDisponibles}, les libellés réellement présents en base.
 * Ce champ accompagne la page plutôt que de vivre dans un endpoint séparé : il alimente un filtre à
 * liste fermée, et une liste d'options qui arriverait d'un autre appel pourrait décrire un état
 * différent de celui qu'on affiche.
 *
 * <p><b>Ce que ce journal ne couvre pas, et il faut le savoir avant de s'y fier</b> : seules les
 * <b>modifications</b> sont historisées (RG-DOS-06). Aucune lecture ne l'est — consulter un dossier
 * sensible ou télécharger une pièce ne laisse aucune trace. L'absence de ligne ne prouve donc pas
 * l'absence d'action ; c'est la raison pour laquelle cet écran s'appelle un journal et non un audit.
 *
 * @param actionsDisponibles libellés distincts présents hors dossier, indépendants des filtres en
 *                           cours — sinon choisir un libellé viderait la liste des autres choix
 */
public record JournalSystemeResponse(
        List<HistoriqueActionResponse> content,
        int totalPages,
        long totalElements,
        List<String> actionsDisponibles) {
}
