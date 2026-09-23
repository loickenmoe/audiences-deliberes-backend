package com.afriland.audiences.notification.service;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mise en forme des coordonnées au format attendu par le Notification Engine.
 *
 * <h2>Téléphone</h2>
 * Une seule écriture est enregistrée : <b>le format international</b>, {@code +237650228936}. Le
 * Guide V1 en accepte trois, mais n'en retenir qu'une évite d'avoir la même coordonnée sous deux
 * formes en base, incomparables entre elles.
 * <p>
 * <b>Cameroun</b> — saisi avec {@code +237}, avec {@code 237} ou sans rien, le numéro doit compter
 * <b>exactement neuf chiffres commençant par 6</b>. Ni plus, ni moins : un numéro amputé d'un chiffre
 * partirait vers quelqu'un d'autre. Tous les mobiles du pays commencent par 6, Camtel compris
 * ({@code 620 XX XX XX}) ; les lignes fixes, en 2, ne reçoivent ni SMS ni WhatsApp et sont écartées.
 * <p>
 * <b>Hors Cameroun</b> — les prestataires étrangers existent, et le moteur délivre au-delà du pays.
 * L'indicatif est alors <b>obligatoire</b> : sans lui, il faudrait le deviner. Le contrôle vérifie la
 * forme internationale (indicatif puis huit à quinze chiffres au total), sans connaître la longueur
 * attendue de chaque pays — cela demanderait une bibliothèque dédiée, que nous n'ajoutons pas tant
 * que la supervision ne montre pas d'échecs sur ces numéros.
 *
 * <h2>Courriel</h2>
 * Contrôle de forme minimal : une partie locale, un arobase, un domaine avec un point.
 */
public final class Coordonnees {

    private static final Pattern SEPARATEURS = Pattern.compile("[\\s.()\\-]");
    /** Indicatif du Cameroun, écrit ou non ; suit la même règle dans les trois cas. */
    private static final Pattern CAMEROUN = Pattern.compile("(?:\\+?237)?(6\\d{8})");
    /** Forme internationale : indicatif d'un chiffre au moins, huit à quinze chiffres en tout. */
    private static final Pattern INTERNATIONAL = Pattern.compile("\\+[1-9]\\d{7,14}");
    private static final Pattern COURRIEL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public static final String INDICATIF_CAMEROUN = "+237";

    private Coordonnees() {
    }

    /**
     * Numéro prêt pour l'Engine, au format international, ou vide s'il est absent ou dans une forme
     * que l'on refuse de deviner.
     */
    public static Optional<String> telephone(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return Optional.empty();
        }
        String numero = SEPARATEURS.matcher(saisie.trim()).replaceAll("");

        var camerounais = CAMEROUN.matcher(numero);
        if (camerounais.matches()) {
            return Optional.of(INDICATIF_CAMEROUN + camerounais.group(1));
        }
        // Un numéro camerounais mal formé ne doit pas être rattrapé par la règle internationale :
        // « +237650228 » y passerait, alors qu'il lui manque trois chiffres.
        if (numero.startsWith("+237") || numero.startsWith("237")) {
            return Optional.empty();
        }
        return INTERNATIONAL.matcher(numero).matches() ? Optional.of(numero) : Optional.empty();
    }

    public static Optional<String> courriel(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return Optional.empty();
        }
        String courriel = saisie.trim();
        return COURRIEL.matcher(courriel).matches() ? Optional.of(courriel) : Optional.empty();
    }
}
