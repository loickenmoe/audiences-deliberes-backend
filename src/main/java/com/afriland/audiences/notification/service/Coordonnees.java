package com.afriland.audiences.notification.service;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formats contact details as expected by the Notification Engine.
 *
 * <h2>Phone</h2>
 * Only the international format is stored ({@code +237650228936}), so the same number never appears
 * in two forms.
 * <p>
 * <b>Cameroon</b>: with {@code +237}, {@code 237} or no prefix, the number must have <b>exactly nine
 * digits starting with 6</b>, since a truncated number would reach someone else. Landlines (starting
 * with 2) receive neither SMS nor WhatsApp and are rejected.
 * <p>
 * <b>Other countries</b>: the country code is <b>mandatory</b>. Only the international shape is
 * checked (code then eight to fifteen digits in total), not each country's expected length.
 *
 * <h2>E-mail</h2>
 * Minimal shape check: a local part, an at sign, a domain with a dot.
 */
public final class Coordonnees {

    private static final Pattern SEPARATEURS = Pattern.compile("[\\s.()\\-]");
    /** Optional Cameroon country code; the same rule applies in all three cases. */
    private static final Pattern CAMEROUN = Pattern.compile("(?:\\+?237)?(6\\d{8})");
    /** International form: country code of at least one digit, eight to fifteen digits in total. */
    private static final Pattern INTERNATIONAL = Pattern.compile("\\+[1-9]\\d{7,14}");
    private static final Pattern COURRIEL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public static final String INDICATIF_CAMEROUN = "+237";

    private Coordonnees() {
    }

    /**
     * Engine-ready number in international format, or empty if missing or ambiguous.
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
        // A malformed Cameroonian number must not fall back to the international rule: "+237650228"
        // would pass although three digits are missing.
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
