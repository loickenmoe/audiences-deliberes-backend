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
 * Photographie des réglages du DJ (Q-118) à un instant donné : canaux allumés et grille par canal.
 * <p>
 * Un canal part pour un événement si et seulement si son interrupteur est allumé <b>et</b> que
 * l'événement figure dans sa grille. Une clé absente ou une valeur inconnue compte comme « rien » :
 * les valeurs sont contrôlées à l'écriture (RegleConfiguration), un écart ici ne peut venir que d'une
 * base modifiée à la main, et ne rien envoyer est alors le défaut le moins dommageable.
 */
public record ReglagesNotification(
        Set<CanalAlerte> canauxInternes,
        Map<CanalAlerte, Set<TypeAlerte>> grilleInternes,
        Set<CanalPrestataire> canauxPrestataires,
        Map<CanalPrestataire, Set<EvenementPrestataire>> grillePrestataires) {

    public static final String CLE_CANAUX_INTERNES = "CANAUX_NOTIFICATION_DEFAUT";
    public static final String CLE_CANAUX_PRESTATAIRES = "CANAUX_NOTIFICATION_PRESTATAIRES";
    public static final String PREFIXE_GRILLE = "GRILLE_NOTIFICATION_";

    /** Canaux internes que la grille couvre ; APPLICATIF n'en fait pas partie, il part toujours. */
    public static final Set<CanalAlerte> CANAUX_INTERNES_EXTERNES = EnumSet.of(CanalAlerte.OUTLOOK, CanalAlerte.TEAMS);

    public boolean envoie(CanalAlerte canal, TypeAlerte type) {
        return canauxInternes.contains(canal) && grilleInternes.getOrDefault(canal, Set.of()).contains(type);
    }

    public boolean envoie(CanalPrestataire canal, EvenementPrestataire evenement) {
        return canauxPrestataires.contains(canal) && grillePrestataires.getOrDefault(canal, Set.of()).contains(evenement);
    }

    /** Construit les réglages à partir des valeurs brutes de {@code configuration_systeme}, par clé. */
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
