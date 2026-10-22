package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.ged.domain.FormatDocument;
import com.afriland.audiences.notification.domain.CanalPrestataire;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.referentiel.domain.TypeValeurConfiguration;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Input rule for a {@code configuration_systeme} key.
 * <p>
 * Several services read these values back and convert them ({@code FraisService}:
 * {@code Double.parseDouble} of the threshold on every expense response; {@code DelibereService}:
 * {@code Integer.parseInt}; {@code GedService}: {@code Long.parseLong} and splitting of
 * {@code FORMATS_GED}). The rule rejects invalid input at the source with a 400, instead of 500
 * errors in unrelated modules, and normalises the value before saving.
 * <p>
 * <b>Bounds are not invented.</b> The specifications (user stories §11.2, CONF01 to CONF07, and
 * US 7.1) set defaults and require a positive integer, with no business maximum. Only the limits
 * the code actually imposes are added:
 * <ul>
 *   <li>minimum 1 everywhere: "positive integer" (US 7.1);</li>
 *   <li>{@code TAILLE_MAX_GED} capped at 10 MB (CONF06): above
 *       {@code spring.servlet.multipart.max-file-size}, Spring rejects the file before the business
 *       check, so the setting would have no effect;</li>
 *   <li>{@code SEUIL_MONTANT_DEFAUT} capped at 12 digits: beyond that, the {@code double} used in
 *       the threshold computation loses precision;</li>
 *   <li>the three counters capped at {@link Integer#MAX_VALUE}, the type the calling code converts to.</li>
 * </ul>
 * An absurd but harmless value is therefore <b>accepted</b>; only values that would break the
 * application are rejected.
 * <p>
 * <b>External notifications.</b> The DJ configures:
 * <ul>
 *   <li>active channels: {@code CANAUX_NOTIFICATION_DEFAUT} for internal users (in-app mandatory,
 *       Outlook and Teams), {@code CANAUX_NOTIFICATION_PRESTATAIRES} for providers (personal email,
 *       SMS, WhatsApp);</li>
 *   <li>the grid: for each channel, the events sent through it ({@code GRILLE_NOTIFICATION_*}).</li>
 * </ul>
 * These lists may be <b>empty</b>: turning off all provider channels, or sending nothing by SMS, is
 * a valid setting, not missing input.
 */
public enum RegleConfiguration {

    SEUIL_MONTANT_DEFAUT(entier(1L, 999_999_999_999L)),
    SEUIL_PROROGATIONS(entier(1L, Integer.MAX_VALUE)),
    DELAI_VALIDATION_CONJOINTE(entier(1L, Integer.MAX_VALUE)),
    DELAI_RAPPEL_CR_AVOCAT(entier(1L, Integer.MAX_VALUE)),
    CHARGE_MAX_AVOCAT(entier(1L, Integer.MAX_VALUE)),
    TAILLE_MAX_GED(entier(1L, 10_485_760L)),
    FORMATS_GED(liste(noms(FormatDocument.values()), null)),
    CANAUX_NOTIFICATION_DEFAUT(liste(noms(CanalAlerte.values()), CanalAlerte.APPLICATIF.name())),
    CANAUX_NOTIFICATION_PRESTATAIRES(listeFacultative(noms(CanalPrestataire.values()))),
    GRILLE_NOTIFICATION_OUTLOOK(listeFacultative(noms(TypeAlerte.values()))),
    GRILLE_NOTIFICATION_TEAMS(listeFacultative(noms(TypeAlerte.values()))),
    GRILLE_NOTIFICATION_COURRIEL_PERSONNEL(listeFacultative(noms(EvenementPrestataire.values()))),
    GRILLE_NOTIFICATION_SMS(listeFacultative(noms(EvenementPrestataire.values()))),
    GRILLE_NOTIFICATION_WHATSAPP(listeFacultative(noms(EvenementPrestataire.values())));

    private final TypeValeurConfiguration type;
    private final Long minimum;
    private final Long maximum;
    private final List<String> valeursPossibles;
    /** Value a list must contain, {@code null} if none is required. */
    private final String valeurObligatoire;
    /** An empty list is a valid setting (no channel, no event), not missing input. */
    private final boolean videAutorise;

    RegleConfiguration(RegleConfiguration.Definition definition) {
        this.type = definition.type();
        this.minimum = definition.minimum();
        this.maximum = definition.maximum();
        this.valeursPossibles = definition.valeursPossibles();
        this.valeurObligatoire = definition.valeurObligatoire();
        this.videAutorise = definition.videAutorise();
    }

    public TypeValeurConfiguration type() {
        return type;
    }

    public Long minimum() {
        return minimum;
    }

    public Long maximum() {
        return maximum;
    }

    public List<String> valeursPossibles() {
        return valeursPossibles;
    }

    public boolean videAutorise() {
        return videAutorise;
    }

    public static Optional<RegleConfiguration> pour(String cle) {
        return Stream.of(values()).filter(regle -> regle.name().equals(cle)).findFirst();
    }

    /**
     * Validates the input and returns its normalised form (spaces removed, list upper-cased and
     * deduplicated), as it must be saved.
     *
     * @throws ApiException 400 if the value does not satisfy the rule
     */
    public String valider(String valeur) {
        String saisie = valeur == null ? "" : valeur.trim();
        if (saisie.isEmpty()) {
            if (videAutorise) {
                return "";
            }
            throw invalide("la valeur est obligatoire");
        }
        return type == TypeValeurConfiguration.ENTIER ? validerEntier(saisie) : validerListe(saisie);
    }

    private String validerEntier(String saisie) {
        if (!saisie.matches("\\d+")) {
            throw invalide("un nombre entier est attendu, sans espace ni séparateur");
        }
        long nombre;
        try {
            nombre = Long.parseLong(saisie);
        } catch (NumberFormatException e) {
            throw invalide("la valeur dépasse les entiers représentables");
        }
        if (nombre < minimum || nombre > maximum) {
            throw invalide("la valeur doit être comprise entre " + minimum + " et " + maximum);
        }
        return String.valueOf(nombre);
    }

    private String validerListe(String saisie) {
        Set<String> retenues = new LinkedHashSet<>();
        for (String element : saisie.split(",", -1)) {
            String normalise = element.trim().toUpperCase(Locale.ROOT);
            if (normalise.isEmpty()) {
                throw invalide("la liste comporte un élément vide");
            }
            if (!valeursPossibles.contains(normalise)) {
                throw invalide("valeur « " + normalise + " » inconnue ; valeurs acceptées : "
                        + String.join(", ", valeursPossibles));
            }
            retenues.add(normalise);
        }
        if (valeurObligatoire != null && !retenues.contains(valeurObligatoire)) {
            throw invalide("la valeur « " + valeurObligatoire + " » ne peut pas être retirée");
        }
        return String.join(",", retenues);
    }

    private ApiException invalide(String raison) {
        return ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Configuration " + name() + " : " + raison);
    }

    private static Definition entier(long minimum, long maximum) {
        return new Definition(TypeValeurConfiguration.ENTIER, minimum, maximum, List.of(), null, false);
    }

    private static Definition liste(List<String> valeursPossibles, String valeurObligatoire) {
        return new Definition(TypeValeurConfiguration.LISTE, null, null, valeursPossibles, valeurObligatoire, false);
    }

    private static Definition listeFacultative(List<String> valeursPossibles) {
        return new Definition(TypeValeurConfiguration.LISTE, null, null, valeursPossibles, null, true);
    }

    private static List<String> noms(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(Enum::name).toList();
    }

    /** Groups a rule's parameters: lets the enum keep a single constructor. */
    private record Definition(TypeValeurConfiguration type, Long minimum, Long maximum,
                              List<String> valeursPossibles, String valeurObligatoire, boolean videAutorise) {
    }
}
