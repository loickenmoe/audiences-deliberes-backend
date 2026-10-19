package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Single entry point to record an action on a case (RG-DOS-06, US 1.6). Business services call
 * {@link #enregistrer} explicitly after each create/update; no AOP, so the trace stays visible in
 * the calling code.
 */
@Service
@RequiredArgsConstructor
public class HistoriqueActionService {

    private final HistoriqueActionRepository historiqueActionRepository;
    private final CurrentUserService currentUserService;

    /**
     * Records an action for the currently authenticated user. The label is derived from
     * {@link TypeActionHistorique}, so code and label cannot diverge.
     *
     * @param dossierId related case, or {@code null} for an action outside any case (e.g. system
     *                  configuration change)
     * @param details   structured details (step name, decision, configuration key...); may be
     *                  {@code null}
     */
    @Transactional
    public HistoriqueAction enregistrer(Long dossierId, TypeActionHistorique code, Map<String, Object> details) {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        return enregistrerPour(utilisateur, dossierId, code, details);
    }

    /** Variant with an explicit author (e.g. a scheduled job acting on behalf of a user). */
    @Transactional
    public HistoriqueAction enregistrerPour(Utilisateur utilisateur, Long dossierId, TypeActionHistorique code,
                                             Map<String, Object> details) {
        HistoriqueAction entry = HistoriqueAction.builder()
                .dossierId(dossierId)
                .utilisateur(utilisateur)
                .code(code)
                .action(code.libelle())
                .details(details)
                .build();
        return historiqueActionRepository.save(entry);
    }
}
