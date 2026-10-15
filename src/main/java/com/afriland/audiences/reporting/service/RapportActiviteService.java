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
 * La fiche d'activité d'une personne (observation 14, Q-133).
 *
 * <p><b>Ce que le métier a demandé le 2026-10-09</b>, en corrigeant mon arbitrage : « le SH et
 * l'Assistante sont à la base des juristes et en plus de cela ont leurs activités propres. Ce
 * rapport devrait refléter fidèlement ce que l'utilisateur a effectué. » Le tableau comparatif de la
 * direction (Q-132) ne peut pas porter cette fidélité : six colonnes y rendent les personnes
 * comparables, quarante-neuf la rendraient illisible. D'où cette seconde lecture, à une personne.
 *
 * <p><b>Chacun sur lui-même, la direction sur quiconque.</b> La règle est tenue <b>ici</b> et non
 * par un {@code @PreAuthorize} : l'annotation dit qui peut toucher à la fonction, le service dit
 * sur qui. C'est le parti déjà retenu ailleurs dans ce module, et le seul qui permette au même
 * endpoint de servir « mon activité » et « l'activité d'un collègue ».
 */
@Service
@RequiredArgsConstructor
public class RapportActiviteService {

    /**
     * Les natures d'action qui ne sont pas une activité de la personne, et que la fiche écarte.
     *
     * <p><b>Définies en creux, et c'est volontaire.</b> La liste des codes comptés est le
     * <b>complément</b> de celle-ci : un code nouveau entre donc dans la fiche sans qu'on y pense.
     * Énumérer les quarante-neuf autres aurait reproduit exactement le défaut de Q-131 - un
     * référentiel fermé qui se tait quand on oublie de l'alimenter, et ne se tait qu'en production.
     *
     * <p>Les quatre exclusions, et leur raison :
     * <ul>
     *   <li>{@code AUDIT_CONSULTE} et {@code ARCHIVE_TELECHARGEE} : <b>lire n'est pas produire</b>.
     *       Les compter ferait monter l'activité de qui consulte beaucoup, ce qui est l'inverse de
     *       ce que le métier veut lire ;</li>
     *   <li>{@code ETAPE_OUVERTE_AUTOMATIQUEMENT} : l'application l'a fait, <b>personne</b> ne l'a
     *       fait. L'attribuer à l'utilisateur qui a déclenché le recours lui prêterait un geste
     *       qu'il n'a pas eu ;</li>
     *   <li>{@code NON_CLASSEE} : ce n'est pas une nature, c'est l'absence de nature. Une ligne
     *       « Action non classée : 7 » n'apprend rien à personne.</li>
     * </ul>
     *
     * <p>{@code AUDIT_EXPORTE} <b>est</b> compté, à l'inverse des deux autres codes d'audit : une
     * extraction scellée est un document produit, pas une lecture.
     */
    static final Set<TypeActionHistorique> CODES_EXCLUS = EnumSet.of(
            TypeActionHistorique.AUDIT_CONSULTE,
            TypeActionHistorique.ARCHIVE_TELECHARGEE,
            TypeActionHistorique.ETAPE_OUVERTE_AUTOMATIQUEMENT,
            TypeActionHistorique.NON_CLASSEE);

    /** Tout le reste. Voir {@link #CODES_EXCLUS} pour savoir pourquoi la liste est prise en creux. */
    static final Set<TypeActionHistorique> CODES_DACTIVITE =
            EnumSet.complementOf(EnumSet.copyOf(CODES_EXCLUS));

    /** Les profils qui peuvent lire l'activité d'autrui. Sur le <b>profil</b>, jamais sur le rôle. */
    private static final Set<String> PROFILS_DIRECTION = Set.of("DJ", "DJA");

    private final HistoriqueActionRepository historiqueActionRepository;
    private final DossierRepository dossierRepository;
    private final CurrentUserService currentUserService;
    private final UtilisateurRepository utilisateurRepository;
    private final CompteAvocatService compteAvocatService;
    private final AvocatRepository avocatRepository;

    /**
     * L'activité d'une personne sur une période.
     *
     * @param utilisateurIdDemande {@code null} pour « moi ». Renseigné, il n'est servi qu'au DJ et à
     *                             la DJA - ou à la personne elle-même, qui a le droit de se demander
     *                             par son identifiant.
     */
    @Transactional(readOnly = true)
    public RapportActiviteResponse produire(Long utilisateurIdDemande, Long avocatIdDemande,
                                            LocalDate debut, LocalDate fin) {
        Utilisateur cible = resoudreCible(utilisateurIdDemande, avocatIdDemande);
        LocalDateTime borneBasse = debut.atStartOfDay();
        // La borne haute inclut toute la journée de fin : comparer un horodatage à une date nue
        // exclurait le dernier jour de la période demandée.
        LocalDateTime borneHaute = fin.plusDays(1).atStartOfDay();

        List<RapportActiviteResponse.LigneActivite> lignes = historiqueActionRepository
                .compterActivitesDe(cible.getId(), CODES_DACTIVITE, borneBasse, borneHaute).stream()
                .map(activite -> new RapportActiviteResponse.LigneActivite(activite.getCode(), activite.getTotal()))
                // Le plus fait d'abord. Une personne seule ne se classe contre personne : l'ordre
                // sert la lecture, il ne désigne pas un meilleur.
                .sorted(Comparator.comparingLong(RapportActiviteResponse.LigneActivite::total).reversed()
                        .thenComparing(ligne -> ligne.code().name()))
                .toList();

        Double delai = lignes.isEmpty()
                ? null
                : historiqueActionRepository.delaiMoyenDeSaisieDe(cible.getId(), borneBasse, borneHaute);

        return new RapportActiviteResponse(cible.getId(), nomDe(cible), profilDe(cible), debut, fin, lignes, delai);
    }

    /**
     * Les actions derrière un décompte, page par page.
     *
     * <p>Même règle de périmètre que {@link #produire} : on ne détaille pas l'activité d'autrui sans
     * être de la direction. Sans quoi le détail serait une porte dérobée vers ce que le décompte
     * protège.
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

        // Les références en un seul aller-retour : une lecture par ligne ferait N+1 requêtes pour
        // une page de dix lignes qui pointent souvent vers le même dossier.
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
     * Qui est regardé, et le droit de le regarder.
     *
     * <p><b>Le périmètre se lit sur le profil, jamais sur le rôle.</b> {@code ROLE_JURISTE} est
     * composite depuis Q-81 : le DJ et la DJA le portent, mais chaque juriste aussi. Un droit qui
     * désigne des personnes - et celui-ci désigne un collègue nommément - ne peut pas s'y adosser,
     * c'est la leçon de Q-126 et de Q-132.
     */
    private Utilisateur resoudreCible(Long utilisateurIdDemande, Long avocatIdDemande) {
        /*
         * Les deux ensemble n'ont pas de sens, et le silence serait pire que le refus : servir l'un
         * en ignorant l'autre laisserait croire qu'on a lu l'activite demandee.
         */
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
         * Un avocat du répertoire est désigné par son <b>inscription</b>, non par son compte : la
         * direction le connaît par le répertoire, où l'identifiant d'utilisateur ne figure pas. Le
         * pont entre les deux existe déjà - c'est celui qui restreint les lectures de l'avocat
         * lui-même (QF-45).
         *
         * <p><b>Le droit est vérifié avant le compte, et l'ordre compte.</b> Une première version
         * résolvait le compte d'abord, puis appliquait la règle : un avocat passant l'inscription
         * d'un confrère obtenait un refus, mais apprenait au passage, par la nature du refus, si ce
         * confrère s'était déjà connecté. {@code estLeCompteDe} répond à la seule question utile -
         * est-ce la sienne ? - sans rien lire du compte visé.
         */
        if (avocatIdDemande != null) {
            Avocat avocat = avocatRepository.findById(avocatIdDemande)
                    .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                            "Avocat introuvable : " + avocatIdDemande));
            if (!PROFILS_DIRECTION.contains(profilDe(moi)) && !compteAvocatService.estLeCompteDe(moi, avocat)) {
                throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                        "Vous ne pouvez consulter que votre propre activité.");
            }
            /*
             * Inscrit au répertoire mais jamais connecté : pas de compte, donc pas une seule ligne
             * d'historique. Le dire distingue « n'a rien fait » de « n'est jamais venu » - une fiche
             * vide confondrait les deux, et c'est la première qui ferait accuser à tort.
             */
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
