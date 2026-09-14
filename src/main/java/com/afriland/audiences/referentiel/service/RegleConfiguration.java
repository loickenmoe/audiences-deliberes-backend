package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.ged.domain.FormatDocument;
import com.afriland.audiences.referentiel.domain.TypeValeurConfiguration;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Règle de saisie d'une clé de {@code configuration_systeme} (Q-91).
 * <p>
 * Sans elle, {@code PUT /configurations/{cle}} accepte n'importe quelle chaîne non vide alors que
 * cinq services relisent ces valeurs et les convertissent : {@code FraisService} ({@code
 * Double.parseDouble} du seuil, sur <b>chaque</b> réponse de frais depuis Q-82), {@code
 * DelibereService} ({@code Integer.parseInt}), {@code GedService} ({@code Long.parseLong} et
 * découpage de {@code FORMATS_GED}). Une saisie erronée du DJ se transformait donc en erreurs 500
 * dans des modules sans rapport avec l'écran de configuration. La règle refuse la valeur à la
 * source, en 400, et la normalise avant enregistrement.
 * <p>
 * <b>Origine des bornes — aucune n'est inventée.</b> Les sources (user stories §11.2, tableau
 * CONF01 à CONF07, et US 7.1 « seuil (entier positif) ») fixent des <b>valeurs par défaut</b> et
 * exigent un entier positif ; elles ne fixent <b>aucun maximum métier</b>. Cette règle s'en tient
 * donc à ce qui est documenté, plus les seules limites que le code impose réellement :
 * <ul>
 *   <li>minimum 1 partout : « entier positif » (US 7.1) ;</li>
 *   <li>{@code TAILLE_MAX_GED} plafonné à 10 Mo : CONF06 donne cette valeur et confie le paramètre
 *       à la DSI « via configuration Spring Boot / MinIO ». Au-delà de
 *       {@code spring.servlet.multipart.max-file-size}, Spring rejette le fichier avant le contrôle
 *       métier — le réglage serait sans effet ;</li>
 *   <li>{@code SEUIL_MONTANT_DEFAUT} plafonné à 12 chiffres : au-delà, le {@code double} du calcul
 *       de seuil perdrait en exactitude ;</li>
 *   <li>les trois compteurs plafonnés à {@link Integer#MAX_VALUE} : c'est le type dans lequel le
 *       code appelant les convertit, rien de plus.</li>
 * </ul>
 * Une valeur absurde mais inoffensive (999 999 prorogations) est donc <b>acceptée</b> : refuser ce
 * que la documentation autorise serait inventer une règle métier. Cette validation ne refuse que ce
 * qui casserait réellement l'application.
 */
public enum RegleConfiguration {

    SEUIL_MONTANT_DEFAUT(entier(1L, 999_999_999_999L)),
    SEUIL_PROROGATIONS(entier(1L, Integer.MAX_VALUE)),
    DELAI_VALIDATION_CONJOINTE(entier(1L, Integer.MAX_VALUE)),
    CHARGE_MAX_AVOCAT(entier(1L, Integer.MAX_VALUE)),
    TAILLE_MAX_GED(entier(1L, 10_485_760L)),
    FORMATS_GED(liste(noms(FormatDocument.values()), null)),
    CANAUX_NOTIFICATION_DEFAUT(liste(noms(CanalAlerte.values()), CanalAlerte.APPLICATIF.name()));

    private final TypeValeurConfiguration type;
    private final Long minimum;
    private final Long maximum;
    private final List<String> valeursPossibles;
    /** Valeur qu'une liste doit obligatoirement contenir, {@code null} si aucune ne l'est. */
    private final String valeurObligatoire;

    RegleConfiguration(RegleConfiguration.Definition definition) {
        this.type = definition.type();
        this.minimum = definition.minimum();
        this.maximum = definition.maximum();
        this.valeursPossibles = definition.valeursPossibles();
        this.valeurObligatoire = definition.valeurObligatoire();
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

    public static Optional<RegleConfiguration> pour(String cle) {
        return Stream.of(values()).filter(regle -> regle.name().equals(cle)).findFirst();
    }

    /**
     * Contrôle la valeur saisie et renvoie sa forme normalisée (espaces retirés, liste en
     * majuscules et sans doublon), telle qu'elle doit être enregistrée.
     *
     * @throws ApiException 400 si la valeur ne respecte pas la règle
     */
    public String valider(String valeur) {
        String saisie = valeur == null ? "" : valeur.trim();
        if (saisie.isEmpty()) {
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
        return new Definition(TypeValeurConfiguration.ENTIER, minimum, maximum, List.of(), null);
    }

    private static Definition liste(List<String> valeursPossibles, String valeurObligatoire) {
        return new Definition(TypeValeurConfiguration.LISTE, null, null, valeursPossibles, valeurObligatoire);
    }

    private static List<String> noms(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(Enum::name).toList();
    }

    /** Regroupe les paramètres d'une règle : une énumération Java n'accepte qu'un constructeur. */
    private record Definition(TypeValeurConfiguration type, Long minimum, Long maximum,
                              List<String> valeursPossibles, String valeurObligatoire) {
    }
}
