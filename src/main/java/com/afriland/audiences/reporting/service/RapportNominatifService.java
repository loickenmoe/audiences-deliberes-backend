package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.reporting.web.dto.RapportNominatifResponse;
import com.afriland.audiences.reporting.web.dto.RapportNominatifResponse.ActiviteNominative;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * L'activité de chacun sur une période (observation 14, N6).
 *
 * <p><b>Tout vient de l'historique, et rien d'ailleurs.</b> {@code historique_action} enregistre qui
 * a fait quoi, quand, sous un code typé : c'est la seule matière dont l'application dispose, et ce
 * service ne compte rien qu'elle n'ait enregistré. Un indicateur qui ne s'y lit pas n'existe pas,
 * et c'est préférable à un chiffre fabriqué.
 *
 * <p><b>L'application compte, le responsable apprécie</b> (métier, 2026-10-07). Pas de note, pas de
 * classement, pas de total global : additionner des dossiers ouverts et des lettres rédigées
 * produirait un nombre sans signification, que sa seule existence rendrait pourtant autoritaire.
 */
@Service
@RequiredArgsConstructor
public class RapportNominatifService {

    /**
     * Les six natures suivies, et le rôle de chacune.
     *
     * <p>Elles disent ce qu'une personne <b>a fait entrer, organisé, consigné, conclu, écrit et
     * contrôlé</b>. Les frais comptent deux codes : le contrôle de conformité et celui
     * d'opportunité sont deux gestes distincts sur la même demande, et n'en retenir qu'un rendrait
     * invisible la moitié du travail.
     */
    private static final Set<TypeActionHistorique> CODES_SUIVIS = Set.of(
            TypeActionHistorique.DOSSIER_CREE,
            TypeActionHistorique.AUDIENCE_PLANIFIEE,
            TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE,
            TypeActionHistorique.DELIBERE_ENREGISTRE,
            TypeActionHistorique.LETTRE_CONSTITUTION_REDIGEE,
            TypeActionHistorique.FRAIS_CONFORMITE_CONTROLEE,
            TypeActionHistorique.FRAIS_OPPORTUNITE_CONTROLEE);

    private final HistoriqueActionRepository historiqueActionRepository;
    private final UtilisateurRepository utilisateurRepository;

    @Transactional(readOnly = true)
    public RapportNominatifResponse produire(LocalDate debut, LocalDate fin) {
        /*
         * La fin est **incluse** : un rapport du 1er au 31 doit compter le 31. Les bornes sont des
         * dates, les actions des instants - on va donc jusqu'au lendemain à minuit, exclu, plutôt
         * que de perdre une journée entière sans que personne le remarque.
         */
        LocalDateTime borneBasse = debut.atStartOfDay();
        LocalDateTime borneHaute = fin.plusDays(1).atStartOfDay();

        Map<Long, Map<TypeActionHistorique, Long>> comptes = new HashMap<>();
        for (var activite : historiqueActionRepository
                .compterActivitesParPersonne(CODES_SUIVIS, borneBasse, borneHaute)) {
            comptes.computeIfAbsent(activite.getUtilisateurId(), id -> new EnumMap<>(TypeActionHistorique.class))
                    .merge(activite.getCode(), activite.getTotal(), Long::sum);
        }
        if (comptes.isEmpty()) {
            return new RapportNominatifResponse(debut, fin, List.of());
        }

        Map<Long, Double> delais = new HashMap<>();
        for (var delai : historiqueActionRepository.delaiMoyenDeSaisieParPersonne(borneBasse, borneHaute)) {
            delais.put(delai.getUtilisateurId(), delai.getDelaiMoyen());
        }

        Map<Long, Utilisateur> personnes = utilisateurRepository.findAllById(comptes.keySet()).stream()
                .collect(java.util.stream.Collectors.toMap(Utilisateur::getId, u -> u));

        return new RapportNominatifResponse(debut, fin, comptes.entrySet().stream()
                .map(entree -> versLigne(entree.getKey(), entree.getValue(),
                        personnes.get(entree.getKey()), delais.get(entree.getKey())))
                // Par nom, et non par volume : un classement ferait du rapport un palmarès.
                .sorted(Comparator.comparing(ActiviteNominative::nom, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList());
    }

    private ActiviteNominative versLigne(Long utilisateurId, Map<TypeActionHistorique, Long> comptes,
                                         Utilisateur personne, Double delaiMoyen) {
        return new ActiviteNominative(
                utilisateurId,
                nomDe(personne),
                personne == null || personne.getProfil() == null ? null : personne.getProfil().getCode(),
                compte(comptes, TypeActionHistorique.DOSSIER_CREE),
                compte(comptes, TypeActionHistorique.AUDIENCE_PLANIFIEE),
                compte(comptes, TypeActionHistorique.AUDIENCE_COMPTE_RENDU_ENREGISTRE),
                compte(comptes, TypeActionHistorique.DELIBERE_ENREGISTRE),
                compte(comptes, TypeActionHistorique.LETTRE_CONSTITUTION_REDIGEE),
                compte(comptes, TypeActionHistorique.FRAIS_CONFORMITE_CONTROLEE)
                        + compte(comptes, TypeActionHistorique.FRAIS_OPPORTUNITE_CONTROLEE),
                delaiMoyen);
    }

    private static long compte(Map<TypeActionHistorique, Long> comptes, TypeActionHistorique code) {
        return comptes.getOrDefault(code, 0L);
    }

    /**
     * Le nom de la personne, ou son identifiant à défaut.
     *
     * <p>Un compte supprimé du référentiel laisse ses traces derrière lui : les compter sous un nom
     * vide les rendrait anonymes, alors que l'audit sert précisément à savoir qui.
     */
    private static String nomDe(Utilisateur personne) {
        if (personne == null) {
            return null;
        }
        String prenom = personne.getPrenom() == null ? "" : personne.getPrenom() + " ";
        return (prenom + (personne.getNom() == null ? "" : personne.getNom())).trim();
    }
}
