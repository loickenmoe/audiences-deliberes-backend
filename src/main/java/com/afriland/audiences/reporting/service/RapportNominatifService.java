package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.reporting.web.dto.RapportAvocatsResponse;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each user's activity over a period, computed solely from {@code historique_action}.
 *
 * <p>The application counts; managers assess. No score, ranking or global total: summing unrelated
 * action types would produce a meaningless number.
 */
@Service
@RequiredArgsConstructor
public class RapportNominatifService {

    /**
     * Tracked action types. Fees count two codes: compliance and appropriateness checks are
     * distinct steps on the same request.
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
    private final AvocatRepository avocatRepository;

    @Transactional(readOnly = true)
    public RapportNominatifResponse produire(LocalDate debut, LocalDate fin) {
        // End date is inclusive: the upper bound is the next day's midnight, exclusive.
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
                // Sorted by name, not volume, to avoid a ranking.
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

    /** The person's full name, or {@code null} for a deleted account (the line keeps its user id). */
    private static String nomDe(Utilisateur personne) {
        if (personne == null) {
            return null;
        }
        String prenom = personne.getPrenom() == null ? "" : personne.getPrenom() + " ";
        return (prenom + (personne.getNom() == null ? "" : personne.getNom())).trim();
    }

    /**
     * The four lawyer-only action types, all guarded by {@code hasRole('AVOCAT')}: grouping on them
     * returns only lawyers without any profile condition.
     */
    private static final Set<TypeActionHistorique> CODES_AVOCAT = Set.of(
            TypeActionHistorique.PUBLICATION_CR_DEPOSE,
            TypeActionHistorique.FRAIS_DEMANDE_DEPOSEE,
            TypeActionHistorique.PUBLICATION_AUTRE_DEPOSEE,
            TypeActionHistorique.EXPLICATION_REPONDUE);

    /**
     * Lawyers' activity over a period, for management. Separate from {@link #produire} because
     * lawyers perform none of the internal actions counted there (they file; legal officers
     * record). Kept in this service to share the period bounds and name ordering.
     */
    @Transactional(readOnly = true)
    public RapportAvocatsResponse produireAvocats(LocalDate debut, LocalDate fin) {
        LocalDateTime borneBasse = debut.atStartOfDay();
        // End date is inclusive, as in the internal report.
        LocalDateTime borneHaute = fin.plusDays(1).atStartOfDay();

        Map<Long, Map<TypeActionHistorique, Long>> comptes = new HashMap<>();
        Map<Long, HistoriqueActionRepository.ActiviteDeposee> personnes = new HashMap<>();
        for (var depot : historiqueActionRepository.compterDepotsParPersonne(
                CODES_AVOCAT, borneBasse, borneHaute)) {
            comptes.computeIfAbsent(depot.getUtilisateurId(),
                            id -> new EnumMap<>(TypeActionHistorique.class))
                    .merge(depot.getCode(), depot.getTotal(), Long::sum);
            personnes.putIfAbsent(depot.getUtilisateurId(), depot);
        }
        if (comptes.isEmpty()) {
            return new RapportAvocatsResponse(debut, fin, List.of());
        }

        /*
         * Directory entries in one query. `compte_keycloak` holds either the login or the Keycloak
         * technical id depending on when the lawyer was registered, so both are looked up, as in
         * `CompteAvocatService`.
         */
        Set<String> comptesCherches = new HashSet<>();
        for (var personne : personnes.values()) {
            if (personne.getIdentifiant() != null) {
                comptesCherches.add(personne.getIdentifiant());
            }
            if (personne.getKeycloakId() != null) {
                comptesCherches.add(personne.getKeycloakId());
            }
        }
        Map<String, Avocat> inscriptions = new HashMap<>();
        if (!comptesCherches.isEmpty()) {
            for (Avocat avocat : avocatRepository.findByCompteKeycloakIn(comptesCherches)) {
                inscriptions.put(avocat.getCompteKeycloak(), avocat);
            }
        }

        return new RapportAvocatsResponse(debut, fin, comptes.entrySet().stream()
                .map(entree -> versLigneAvocat(entree.getKey(), entree.getValue(),
                        personnes.get(entree.getKey()), inscriptions))
                // Sorted by name, not volume: workload reflects the bank's assignments, not merit.
                .sorted(Comparator.comparing(RapportAvocatsResponse.ActiviteAvocat::nom,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList());
    }

    private RapportAvocatsResponse.ActiviteAvocat versLigneAvocat(
            Long utilisateurId,
            Map<TypeActionHistorique, Long> comptes,
            HistoriqueActionRepository.ActiviteDeposee personne,
            Map<String, Avocat> inscriptions) {
        Avocat avocat = personne == null ? null : trouverInscription(personne, inscriptions);
        // Directory name first; if the entry was removed, fall back to the account name and keep
        // counting the filings.
        String nom = avocat != null ? avocat.getNom() : nomDuCompte(personne);
        return new RapportAvocatsResponse.ActiviteAvocat(
                utilisateurId,
                avocat == null ? null : avocat.getId(),
                nom,
                compte(comptes, TypeActionHistorique.PUBLICATION_CR_DEPOSE),
                compte(comptes, TypeActionHistorique.FRAIS_DEMANDE_DEPOSEE),
                compte(comptes, TypeActionHistorique.PUBLICATION_AUTRE_DEPOSEE),
                compte(comptes, TypeActionHistorique.EXPLICATION_REPONDUE));
    }

    private static Avocat trouverInscription(HistoriqueActionRepository.ActiviteDeposee personne,
                                             Map<String, Avocat> inscriptions) {
        Avocat parIdentifiant = personne.getIdentifiant() == null
                ? null
                : inscriptions.get(personne.getIdentifiant());
        if (parIdentifiant != null) {
            return parIdentifiant;
        }
        return personne.getKeycloakId() == null ? null : inscriptions.get(personne.getKeycloakId());
    }

    /** Account name, used when the directory no longer lists the lawyer. */
    private static String nomDuCompte(HistoriqueActionRepository.ActiviteDeposee personne) {
        if (personne == null) {
            return null;
        }
        String prenom = personne.getPrenom() == null ? "" : personne.getPrenom();
        String nom = personne.getNom() == null ? "" : personne.getNom();
        String complet = (prenom + " " + nom).trim();
        return complet.isEmpty() ? null : complet;
    }
}
