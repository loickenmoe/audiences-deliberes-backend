package com.afriland.audiences.common.audit;

import com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse;

import java.util.List;

/**
 * A page of audit results, plus {@code actionsDisponibles}: the action types actually present, used
 * for a closed-list filter. That list ignores the other active filters so the user can switch choice.
 *
 * <p>Only modifications are recorded (RG-DOS-06); consultations are not, except for this screen.
 * A missing line therefore does not prove that an action did not happen.
 */
public record AuditResponse(
        List<HistoriqueActionResponse> content,
        int totalPages,
        long totalElements,
        List<OptionAction> actionsDisponibles) {

    /**
     * A filter option: the code is sent to the server, the label is displayed. The label comes from
     * the enum, not from the row, since rows migrated by V18 still carry their legacy wording.
     */
    public record OptionAction(TypeActionHistorique code, String libelle) {
        public static OptionAction de(TypeActionHistorique code) {
            return new OptionAction(code, code.libelle());
        }
    }
}
