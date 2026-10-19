package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.notification.domain.CanalPrestataire;
import com.afriland.audiences.notification.domain.EvenementPrestataire;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Snapshot of the notification settings: enabled channels and the event grid of each channel.
 * <p>
 * A channel fires for an event only if its switch is on <b>and</b> the event is in its grid. A missing
 * key or unknown value counts as "nothing": values are validated on write (RegleConfiguration), so a
 * mismatch can only come from a hand-edited database, where sending nothing is the safest default.
 */
public record ReglagesNotification(
        Set<CanalAlerte> canauxInternes,
        Map<CanalAlerte, Set<TypeAlerte>> grilleInternes,
        Set<CanalPrestataire> canauxPrestataires,
        Map<CanalPrestataire, Set<EvenementPrestataire>> grillePrestataires) {

    public static final String CLE_CANAUX_INTERNES = "CANAUX_NOTIFICATION_DEFAUT";
    public static final String CLE_CANAUX_PRESTATAIRES = "CANAUX_NOTIFICATION_PRESTATAIRES";
    public static final String PREFIXE_GRILLE = "GRILLE_NOTIFICATION_";

    /** Internal channels covered by the grid; APPLICATIF is not one of them, it always fires. */
    public static final Set<CanalAlerte> CANAUX_INTERNES_EXTERNES = EnumSet.of(CanalAlerte.OUTLOOK, CanalAlerte.TEAMS);

    public boolean envoie(CanalAlerte canal, TypeAlerte type) {
        return canauxInternes.contains(canal) && grilleInternes.getOrDefault(canal, Set.of()).contains(type);
    }

    public boolean envoie(CanalPrestataire canal, EvenementPrestataire evenement) {
        return canauxPrestataires.contains(canal) && grillePrestataires.getOrDefault(canal, Set.of()).contains(evenement);
    }

    /** Builds the settings from the raw {@code configuration_systeme} values, by key. */
    public static ReglagesNotification depuis(Map<String, String> valeurs) {
        Map<CanalAlerte, Set<TypeAlerte>> grilleInternes = new EnumMap<>(CanalAlerte.class);
        for (CanalAlerte canal : CANAUX_INTERNES_EXTERNES) {
            grilleInternes.put(canal, lire(valeurs.get(PREFIXE_GRILLE + canal.name()), TypeAlerte.class));
        }
        Map<CanalPrestataire, Set<EvenementPrestataire>> grillePrestataires = new EnumMap<>(CanalPrestataire.class);
        for (CanalPrestataire canal : CanalPrestataire.values()) {
            grillePrestataires.put(canal, lire(valeurs.get(PREFIXE_GRILLE + canal.name()), EvenementPrestataire.class));
        }
        return new ReglagesNotification(
                lire(valeurs.get(CLE_CANAUX_INTERNES), CanalAlerte.class),
                grilleInternes,
                lire(valeurs.get(CLE_CANAUX_PRESTATAIRES), CanalPrestataire.class),
                grillePrestataires);
    }

    private static <E extends Enum<E>> Set<E> lire(String valeur, Class<E> type) {
        Set<E> resultat = EnumSet.noneOf(type);
        if (valeur == null) {
            return resultat;
        }
        Arrays.stream(valeur.split(","))
                .map(element -> element.trim().toUpperCase(Locale.ROOT))
                .map(element -> valeurDe(type, element))
                .flatMap(Optional::stream)
                .forEach(resultat::add);
        return resultat;
    }

    private static <E extends Enum<E>> Optional<E> valeurDe(Class<E> type, String nom) {
        return Arrays.stream(type.getEnumConstants()).filter(constante -> constante.name().equals(nom)).findFirst();
    }
}
