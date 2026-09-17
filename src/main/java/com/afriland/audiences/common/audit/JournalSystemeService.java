package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.dossier.mapper.HistoriqueActionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Lecture du journal des actions historisées <b>hors dossier</b> (Q-110).
 *
 * <p><b>Le manque qu'il comble.</b> RG-DOS-06 historise chaque modification, mais l'unique point de
 * lecture — {@code GET /dossiers/{id}/historique} — part d'un dossier. Les actions qui n'en ont pas
 * étaient donc écrites et jamais relues : modifications de paramètres système, indexations de
 * jurisprudence. Autrement dit, « qui a abaissé le seuil de validation conjointe ? » exigeait une
 * requête SQL d'un administrateur de base. Q-108, en ouvrant l'administration des paramètres à la
 * DJA, a doublé le nombre de détenteurs de ce pouvoir sans rien changer à cette impossibilité.
 *
 * <p><b>Ce que ce service ne fait pas, et c'est délibéré.</b> Il ne sert pas de vue transversale de
 * l'historique des dossiers. {@link Perimetre} rend l'exclusion structurelle plutôt que
 * conventionnelle : ouvrir un jour cette vue serait une décision, pas l'effet d'un paramètre oublié.
 * Deux préalables la conditionnent — un code d'action typé (QF-24), sans lequel tout filtre par
 * nature d'action ment par omission, et {@code NF-CONF-01} (RGPD, conservation), que les sources
 * renvoient explicitement à la DRH.
 */
@Service
@RequiredArgsConstructor
public class JournalSystemeService {

    /** Le journal se lit du plus récent au plus ancien, `id` départageant les ex æquo (Q-92). */
    private static final Sort TRI = Sort.by(Sort.Direction.DESC, "dateAction").and(Sort.by(Sort.Direction.DESC, "id"));

    private final HistoriqueActionRepository historiqueActionRepository;
    private final HistoriqueActionMapper historiqueActionMapper;

    @Transactional(readOnly = true)
    public JournalSystemeResponse consulter(Long utilisateurId, String action, LocalDate dateDebut,
                                               LocalDate dateFin, int page, int size) {
        if (dateDebut != null && dateFin != null && dateDebut.isAfter(dateFin)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début ne peut pas être postérieure à la date de fin");
        }

        Page<HistoriqueAction> resultat = historiqueActionRepository.findAll(
                HistoriqueActionSpecifications.withFilters(
                        Perimetre.HORS_DOSSIER, null, utilisateurId, action, dateDebut, dateFin),
                PageRequest.of(page, size, TRI));

        /*
         * Les libellés proposés au filtre sont ceux de **tout** le journal, pas ceux de la page ni
         * du résultat filtré : sinon choisir un libellé ferait disparaître les autres choix, et
         * l'utilisateur ne pourrait plus changer d'avis sans vider ses filtres.
         */
        return new JournalSystemeResponse(
                resultat.getContent().stream().map(historiqueActionMapper::toResponse).toList(),
                resultat.getTotalPages(),
                resultat.getTotalElements(),
                historiqueActionRepository.actionsHorsDossier());
    }
}
