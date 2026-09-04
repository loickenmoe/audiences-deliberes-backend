package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Point d'entrée unique pour historiser une action sur un dossier (RG-DOS-06, US 1.6). Les
 * services métier des modules fonctionnels (M4+) appellent {@link #enregistrer} après chaque
 * opération de création/modification — pas d'AOP/interception implicite, pour garder la trace
 * explicite dans le code appelant (cf. SYSTEM_ARCHITECTURE.md §3.2).
 */
@Service
@RequiredArgsConstructor
public class HistoriqueActionService {

    private final HistoriqueActionRepository historiqueActionRepository;
    private final CurrentUserService currentUserService;

    /**
     * Historise une action pour l'utilisateur actuellement authentifié.
     *
     * @param dossierId identifiant du dossier concerné (peut être {@code null} pour une action
     *                  hors dossier — ex. modification d'une configuration système)
     * @param action    description courte de l'action (ex. "Création du dossier")
     * @param details   détails structurés (anciennes/nouvelles valeurs) ; peut être {@code null}
     */
    @Transactional
    public HistoriqueAction enregistrer(Long dossierId, String action, Map<String, Object> details) {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        return enregistrerPour(utilisateur, dossierId, action, details);
    }

    /**
     * Variante permettant de préciser explicitement l'auteur (ex. action déclenchée par un
     * traitement planifié agissant pour le compte d'un utilisateur donné).
     */
    @Transactional
    public HistoriqueAction enregistrerPour(Utilisateur utilisateur, Long dossierId, String action,
                                             Map<String, Object> details) {
        HistoriqueAction entry = HistoriqueAction.builder()
                .dossierId(dossierId)
                .utilisateur(utilisateur)
                .action(action)
                .details(details)
                .build();
        return historiqueActionRepository.save(entry);
    }
}
