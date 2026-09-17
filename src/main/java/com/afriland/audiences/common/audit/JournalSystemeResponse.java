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
 * @param actionsDisponibles natures d'action présentes hors dossier, indépendantes des filtres en
 *                           cours — sinon choisir une nature viderait la liste des autres choix
 */
public record JournalSystemeResponse(
        List<HistoriqueActionResponse> content,
        int totalPages,
        long totalElements,
        List<OptionAction> actionsDisponibles) {

    /**
     * Une entrée du filtre : le <b>code</b> qui part au serveur, le <b>libellé</b> qui s'affiche.
     *
     * <p>Le libellé est dérivé de l'énumération plutôt que lu en base : les lignes reprises par la
     * migration V18 portent encore leur ancienne phrase, et une même nature d'action se serait
     * présentée plusieurs fois dans la liste — « Modification de la configuration SEUIL_PROROGATIONS »
     * et « … TAILLE_MAX_GED » pour un unique code.
     */
    public record OptionAction(TypeActionHistorique code, String libelle) {
        public static OptionAction de(TypeActionHistorique code) {
            return new OptionAction(code, code.libelle());
        }
    }
}
