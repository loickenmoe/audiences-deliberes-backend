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
 * Computes the external channels actually used for a recipient and an event.
 * <ul>
 *   <li><b>Internal user</b>: Outlook and Teams, per {@code CANAUX_NOTIFICATION_DEFAUT} and their grid.
 *       Only the Keycloak directory address is known; it serves both channels (the Teams recipient is
 *       the Azure AD address, Guide V1).</li>
 *   <li><b>Provider</b>: personal e-mail, SMS and WhatsApp, per
 *       {@code CANAUX_NOTIFICATION_PRESTATAIRES} and their grid, provided the provider consented.</li>
 * </ul>
 * A selected channel without a usable contact is returned with its reason so it shows in monitoring.
 * The in-app channel is not concerned; it always fires.
 */
@Service
@RequiredArgsConstructor
public class RoutageNotification {

    /** Profile code of lawyers with an account: they follow the provider grid. */
    static final String PROFIL_AVOCAT = "AVOCAT";

    private final ReglagesNotificationService reglagesNotificationService;

    /**
     * External channels of an alert addressed to an application user. A lawyer with an account is
     * never notified via Outlook or Teams (provider channels apply, see {@link #pourPrestataire}), so
     * the list is then empty.
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

    /** External channels of an event addressed to a lawyer or another provider. */
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
     * What routing needs to know about a provider, whatever its type.
     *
     * @param consentement provider's consent to be notified outside the application
     *                     ({@code AutrePrestataire.notification}, or the lawyer's equivalent field)
     */
    public record CoordonneesPrestataire(String courriel, String telephone, boolean consentement) {
    }
}
