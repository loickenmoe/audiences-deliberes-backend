package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.notification.domain.CanalPrestataire;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.service.Route.MotifNonJoignable;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Calcule, pour un destinataire et un événement, les canaux externes qui partent réellement
 * (NOTIFICATION_ENGINE_PLAN.md, étape 4).
 * <ul>
 *   <li><b>Utilisateur interne</b> (D1) : Outlook et Teams, selon {@code CANAUX_NOTIFICATION_DEFAUT} et
 *       leur grille. Seule l'adresse de l'annuaire Keycloak est connue, qui sert aux deux canaux (le
 *       destinataire Teams est l'adresse Azure AD, Guide V1).</li>
 *   <li><b>Prestataire</b> (D2) : courriel personnel, SMS et WhatsApp, selon
 *       {@code CANAUX_NOTIFICATION_PRESTATAIRES} et leur grille, à condition qu'il ait consenti (D15).</li>
 * </ul>
 * Un canal retenu sans coordonnée utilisable n'est pas retiré en silence : il revient avec son motif,
 * pour être visible en supervision (D14). Le canal applicatif n'est pas concerné, il part toujours.
 */
@Service
@RequiredArgsConstructor
public class RoutageNotification {

    /** Code du profil des avocats disposant d'un compte : ils suivent la grille des prestataires. */
    static final String PROFIL_AVOCAT = "AVOCAT";

    private final ReglagesNotificationService reglagesNotificationService;

    /**
     * Canaux externes d'une alerte adressée à un utilisateur de l'application.
     * <p>
     * Un avocat qui a un compte n'est jamais notifié par Outlook ou Teams : ses canaux externes sont
     * ceux des prestataires (D2), calculés par {@link #pourPrestataire}. La liste est alors vide.
     */
    public List<Route> pourUtilisateur(TypeAlerte type, Utilisateur utilisateur) {
        if (estAvocat(utilisateur)) {
            return List.of();
        }
        ReglagesNotification reglages = reglagesNotificationService.charger();
        List<Route> routes = new ArrayList<>();
        for (CanalAlerte canal : ReglagesNotification.CANAUX_INTERNES_EXTERNES) {
            if (reglages.envoie(canal, type)) {
                CanalEngine canalEngine = canal == CanalAlerte.TEAMS ? CanalEngine.TEAMS : CanalEngine.EMAIL;
                routes.add(route(canal.name(), canalEngine, utilisateur.getEmail(), Coordonnees::courriel));
            }
        }
        return routes;
    }

    /** Canaux externes d'un événement adressé à un avocat ou à un autre prestataire. */
    public List<Route> pourPrestataire(EvenementPrestataire evenement, CoordonneesPrestataire coordonnees) {
        if (!coordonnees.consentement()) {
            return List.of();
        }
        ReglagesNotification reglages = reglagesNotificationService.charger();
        List<Route> routes = new ArrayList<>();
        for (CanalPrestataire canal : CanalPrestataire.values()) {
            if (reglages.envoie(canal, evenement)) {
                routes.add(canal == CanalPrestataire.COURRIEL_PERSONNEL
                        ? route(canal.name(), canal.canalEngine(), coordonnees.courriel(), Coordonnees::courriel)
                        : route(canal.name(), canal.canalEngine(), coordonnees.telephone(), Coordonnees::telephone));
            }
        }
        return routes;
    }

    private static Route route(String reglage, CanalEngine canal, String saisie,
                               Function<String, Optional<String>> mettreEnForme) {
        if (saisie == null || saisie.isBlank()) {
            return new Route(reglage, canal, null, MotifNonJoignable.COORDONNEE_ABSENTE);
        }
        return mettreEnForme.apply(saisie)
                .map(destinataire -> new Route(reglage, canal, destinataire, null))
                .orElseGet(() -> new Route(reglage, canal, null, MotifNonJoignable.COORDONNEE_INVALIDE));
    }

    private static boolean estAvocat(Utilisateur utilisateur) {
        return utilisateur.getProfil() != null && PROFIL_AVOCAT.equals(utilisateur.getProfil().getCode());
    }

    /**
     * Ce que le routage doit savoir d'un prestataire, quel que soit son type.
     *
     * @param consentement accord du prestataire pour être notifié hors de l'application (D15) :
     *                     {@code AutrePrestataire.notification}, et pour l'avocat le champ ajouté à l'étape 7
     */
    public record CoordonneesPrestataire(String courriel, String telephone, boolean consentement) {
    }
}
