package com.afriland.audiences.reporting.service;

import com.afriland.audiences.common.audit.HistoriqueAction;
import com.afriland.audiences.common.audit.HistoriqueActionRepository;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.reporting.web.dto.ActionDetailleeResponse;
import com.afriland.audiences.reporting.web.dto.RapportActiviteResponse;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One person's activity sheet, reflecting everything they actually did (the comparative per-user
 * report only shows a fixed set of columns).
 *
 * <p>Everyone may view their own activity; management may view anyone's. This rule is enforced
 * here rather than in {@code @PreAuthorize}, which only gates the function, so one endpoint serves
 * both "my activity" and "a colleague's activity".
 */
@Service
@RequiredArgsConstructor
public class RapportActiviteService {

    /**
     * Action types excluded from the sheet. Counted codes are defined as the complement of this
     * set, so any new code is included automatically.
     * <ul>
     *   <li>{@code AUDIT_CONSULTE}, {@code ARCHIVE_TELECHARGEE}: reading is not producing;</li>
     *   <li>{@code ETAPE_OUVERTE_AUTOMATIQUEMENT}: performed by the application, not a person;</li>
     *   <li>{@code NON_CLASSEE}: not a meaningful action type.</li>
     * </ul>
     * {@code AUDIT_EXPORTE} is counted: a sealed export is a produced document.
     */
    static final Set<TypeActionHistorique> CODES_EXCLUS = EnumSet.of(
            TypeActionHistorique.AUDIT_CONSULTE,
            TypeActionHistorique.ARCHIVE_TELECHARGEE,
            TypeActionHistorique.ETAPE_OUVERTE_AUTOMATIQUEMENT,
            TypeActionHistorique.NON_CLASSEE);

    /** Every code not in {@link #CODES_EXCLUS}. */
    static final Set<TypeActionHistorique> CODES_DACTIVITE =
            EnumSet.complementOf(EnumSet.copyOf(CODES_EXCLUS));

    /** Profiles allowed to view others' activity. Checked on the profile, never on the role. */
    private static final Set<String> PROFILS_DIRECTION = Set.of("DJ", "DJA");

    private final HistoriqueActionRepository historiqueActionRepository;
    private final DossierRepository dossierRepository;
    private final CurrentUserService currentUserService;
    private final UtilisateurRepository utilisateurRepository;
    private final CompteAvocatService compteAvocatService;
    private final AvocatRepository avocatRepository;

    /**
     * A person's activity over a period.
     *
     * @param utilisateurIdDemande {@code null} for the current user; otherwise served only to DJ/DJA
     *                             or to the person themselves
     */
    @Transactional(readOnly = true)
    public RapportActiviteResponse produire(Long utilisateurIdDemande, Long avocatIdDemande,
                                            LocalDate debut, LocalDate fin) {
        Utilisateur cible = resoudreCible(utilisateurIdDemande, avocatIdDemande);
        LocalDateTime borneBasse = debut.atStartOfDay();
        // Upper bound is exclusive midnight of the next day so the whole end date is included.
        LocalDateTime borneHaute = fin.plusDays(1).atStartOfDay();

        List<RapportActiviteResponse.LigneActivite> lignes = historiqueActionRepository
                .compterActivitesDe(cible.getId(), CODES_DACTIVITE, borneBasse, borneHaute).stream()
                .map(activite -> new RapportActiviteResponse.LigneActivite(activite.getCode(), activite.getTotal()))
                // Most frequent first, for readability only.
                .sorted(Comparator.comparingLong(RapportActiviteResponse.LigneActivite::total).reversed()
                        .thenComparing(ligne -> ligne.code().name()))
                .toList();

        Double delai = lignes.isEmpty()
                ? null
                : historiqueActionRepository.delaiMoyenDeSaisieDe(cible.getId(), borneBasse, borneHaute);

        return new RapportActiviteResponse(cible.getId(), nomDe(cible), profilDe(cible), debut, fin, lignes, delai);
    }

    /**
     * Paged actions behind a count. Same access rule as {@link #produire}, so the detail cannot
     * bypass what the count protects.
     */
    @Transactional(readOnly = true)
    public Page<ActionDetailleeResponse> detailler(Long utilisateurIdDemande, Long avocatIdDemande,
                                                   TypeActionHistorique code,
                                                   LocalDate debut, LocalDate fin, Pageable pageable) {
        if (CODES_EXCLUS.contains(code)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Cette nature d'action n'est pas comptée dans la fiche d'activité.");
        }
        Utilisateur cible = resoudreCible(utilisateurIdDemande, avocatIdDemande);
        Page<HistoriqueAction> actions = historiqueActionRepository.listerActionsDe(
                cible.getId(), code, debut.atStartOfDay(), fin.plusDays(1).atStartOfDay(), pageable);

        // Case references loaded in a single query to avoid N+1.
        Map<Long, String> references = referencesDe(actions.getContent());
        return actions.map(action -> new ActionDetailleeResponse(
                action.getId(),
                action.getCode(),
                action.getAction(),
                action.getDateAction(),
                action.getDossierId(),
                action.getDossierId() == null ? null : references.get(action.getDossierId())));
    }

    private Map<Long, String> referencesDe(List<HistoriqueAction> actions) {
        List<Long> identifiants = actions.stream()
                .map(HistoriqueAction::getDossierId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (identifiants.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> references = new HashMap<>();
        for (var dossier : dossierRepository.findAllById(identifiants)) {
            references.put(dossier.getId(), dossier.getReference());
        }
        return references;
    }

    /**
     * Resolves the viewed person and checks the right to view them. Checked on the profile, never
     * on the role: {@code ROLE_JURISTE} is composite and also held by DJ/DJA.
     */
    private Utilisateur resoudreCible(Long utilisateurIdDemande, Long avocatIdDemande) {
        // Both ids together are rejected rather than silently ignoring one.
        if (utilisateurIdDemande != null && avocatIdDemande != null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Demandez une personne par son compte ou par son inscription au repertoire, pas les deux.");
        }
        Utilisateur moi = currentUserService.getCurrentUser();
        if (moi == null) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Votre compte n'est pas rattaché au référentiel des utilisateurs.");
        }
        /*
         * A lawyer is addressed by directory entry, not user account. The access check runs before
         * resolving the account: otherwise the error type would reveal to another lawyer whether
         * a colleague has ever logged in.
         */
        if (avocatIdDemande != null) {
            Avocat avocat = avocatRepository.findById(avocatIdDemande)
                    .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                            "Avocat introuvable : " + avocatIdDemande));
            if (!PROFILS_DIRECTION.contains(profilDe(moi)) && !compteAvocatService.estLeCompteDe(moi, avocat)) {
                throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                        "Vous ne pouvez consulter que votre propre activité.");
            }
            // Listed but never logged in: no account, hence no history. A dedicated error
            // distinguishes "never came" from "did nothing".
            return compteAvocatService.utilisateurDe(avocat)
                    .orElseThrow(() -> ApiException.notFound(ErrorCodes.AVOCAT_NON_RATTACHE,
                            "Cet avocat ne s'est jamais connecté : aucune activité n'est tracée sous son nom."));
        }
        if (utilisateurIdDemande == null || utilisateurIdDemande.equals(moi.getId())) {
            return moi;
        }
        if (!PROFILS_DIRECTION.contains(profilDe(moi))) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Vous ne pouvez consulter que votre propre activité.");
        }
        return utilisateurRepository.findById(utilisateurIdDemande)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Utilisateur introuvable : " + utilisateurIdDemande));
    }

    private static String nomDe(Utilisateur personne) {
        String prenom = personne.getPrenom() == null ? "" : personne.getPrenom();
        String nom = personne.getNom() == null ? "" : personne.getNom();
        String complet = (prenom + " " + nom).trim();
        return complet.isEmpty() ? null : complet;
    }

    private static String profilDe(Utilisateur personne) {
        return personne.getProfil() == null ? null : personne.getProfil().getCode();
    }
}
