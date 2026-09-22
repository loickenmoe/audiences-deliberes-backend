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
     * <p><b>Le libellé n'est plus transmis</b> depuis Q-111 : il est porté par
     * {@link TypeActionHistorique} et dérivé ici. Deux arguments côte à côte — un code et une phrase
     * censée le décrire — auraient fini par diverger ; l'écart est désormais impossible.
     *
     * @param code      nature typée de l'action, sur laquelle porteront les filtres d'audit
     * @param dossierId identifiant du dossier concerné (peut être {@code null} pour une action
     *                  hors dossier — ex. modification d'une configuration système)
     * @param details   détails structurés, y compris ce qui figurait autrefois dans la phrase
     *                  (nom de l'étape, sens d'une décision, clé de configuration) ; peut être
     *                  {@code null}
     */
    @Transactional
    public HistoriqueAction enregistrer(Long dossierId, TypeActionHistorique code, Map<String, Object> details) {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        return enregistrerPour(utilisateur, dossierId, code, details);
    }

    /**
     * Variante permettant de préciser explicitement l'auteur (ex. action déclenchée par un
     * traitement planifié agissant pour le compte d'un utilisateur donné).
     */
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
