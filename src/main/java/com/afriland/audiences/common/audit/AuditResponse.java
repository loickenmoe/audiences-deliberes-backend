package com.afriland.audiences.common.audit;

import com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse;

import java.util.List;

/**
 * Une page d'audit (Q-112).
 *
 * <p>Même forme qu'une page paginée, plus {@code actionsDisponibles} : les natures réellement
 * présentes dans le périmètre consulté, qui alimentent un filtre à <b>liste fermée</b>. Cette liste
 * ne dépend pas des autres filtres en cours — sinon en choisir une ferait disparaître les autres et
 * l'on ne pourrait plus changer d'avis sans tout vider.
 *
 * <p><b>Ce que l'audit ne couvre pas, et qu'il faut savoir avant de s'y fier.</b> Seules les
 * <b>modifications</b> sont historisées (RG-DOS-06) ; <b>aucune consultation ne l'est</b>, à
 * l'exception de celle de cet écran. Ouvrir un dossier sensible ou télécharger une pièce ne laisse
 * aucune trace : l'absence de ligne ne prouve donc pas l'absence d'action. C'est une limite connue
 * et écrite, dont la levée éventuelle fait l'objet d'un jalon distinct.
 */
public record AuditResponse(
        List<HistoriqueActionResponse> content,
        int totalPages,
        long totalElements,
        List<OptionAction> actionsDisponibles) {

    /**
     * Une entrée du filtre par nature d'action : le <b>code</b> part au serveur, le <b>libellé</b>
     * s'affiche.
     *
     * <p>Le libellé vient de l'énumération et non de la ligne : les entrées reprises par V18 portent
     * encore leur ancienne phrase, et une même nature se serait présentée plusieurs fois.
     */
    public record OptionAction(TypeActionHistorique code, String libelle) {
        public static OptionAction de(TypeActionHistorique code) {
            return new OptionAction(code, code.libelle());
        }
    }
}
