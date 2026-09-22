package com.afriland.audiences.notification.service;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mise en forme des coordonnées au format attendu par le Notification Engine.
 * <p>
 * <b>Téléphone</b> : le Guide V1 accepte trois formes, E.164 ({@code +237655201587}), un numéro
 * commençant par 237 ({@code 237655201587}) ou par 6 ({@code 655201587}). On retire seulement les
 * séparateurs de saisie (espaces, points, tirets, parenthèses) et l'on n'accepte que ces trois formes ;
 * un numéro local à 9 chiffres est complété de l'indicatif 237, que le Guide traite comme équivalent.
 * Tout le reste est refusé plutôt que deviné : un SMS parti vers un mauvais numéro ne se rattrape pas.
 * <p>
 * <b>Courriel</b> : contrôle de forme minimal (une partie locale, un arobase, un domaine avec un point).
 */
public final class Coordonnees {

    private static final Pattern SEPARATEURS = Pattern.compile("[\\s.()\\-]");
    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");
    private static final Pattern INDICATIF_237 = Pattern.compile("237\\d{9}");
    private static final Pattern LOCAL_6 = Pattern.compile("6\\d{8}");
    private static final Pattern COURRIEL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private Coordonnees() {
    }

    /** Numéro prêt pour l'Engine, ou vide s'il est absent ou dans une forme que le Guide n'accepte pas. */
    public static Optional<String> telephone(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return Optional.empty();
        }
        String numero = SEPARATEURS.matcher(saisie.trim()).replaceAll("");
        if (E164.matcher(numero).matches() || INDICATIF_237.matcher(numero).matches()) {
            return Optional.of(numero);
        }
        if (LOCAL_6.matcher(numero).matches()) {
            return Optional.of("237" + numero);
        }
        return Optional.empty();
    }

    public static Optional<String> courriel(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return Optional.empty();
        }
        String courriel = saisie.trim();
        return COURRIEL.matcher(courriel).matches() ? Optional.of(courriel) : Optional.empty();
    }
}
