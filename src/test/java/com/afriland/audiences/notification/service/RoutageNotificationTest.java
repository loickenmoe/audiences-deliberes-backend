package com.afriland.audiences.notification.service;

import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.notification.domain.CanalPrestataire;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.passerelle.CanalEngine;
import com.afriland.audiences.notification.service.Route.MotifNonJoignable;
import com.afriland.audiences.notification.service.RoutageNotification.CoordonneesPrestataire;
import com.afriland.audiences.referentiel.domain.Profil;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutageNotificationTest {

    private static final String TOUS_TYPES = String.join(",",
            java.util.Arrays.stream(TypeAlerte.values()).map(Enum::name).toList());
    private static final String TOUS_EVENEMENTS = String.join(",",
            java.util.Arrays.stream(EvenementPrestataire.values()).map(Enum::name).toList());

    @Mock private ReglagesNotificationService reglagesNotificationService;
    @InjectMocks private RoutageNotification routage;

    /** Initial V28 settings, editable key by key. */
    private static Map<String, String> reglagesInitiaux() {
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("CANAUX_NOTIFICATION_DEFAUT", "APPLICATIF,OUTLOOK");
        valeurs.put("CANAUX_NOTIFICATION_PRESTATAIRES", "COURRIEL_PERSONNEL,SMS");
        valeurs.put("GRILLE_NOTIFICATION_OUTLOOK", TOUS_TYPES);
        valeurs.put("GRILLE_NOTIFICATION_TEAMS", TOUS_TYPES);
        valeurs.put("GRILLE_NOTIFICATION_COURRIEL_PERSONNEL", TOUS_EVENEMENTS);
        valeurs.put("GRILLE_NOTIFICATION_SMS", TOUS_EVENEMENTS);
        valeurs.put("GRILLE_NOTIFICATION_WHATSAPP", TOUS_EVENEMENTS);
        return valeurs;
    }

    private void regler(Map<String, String> valeurs) {
        when(reglagesNotificationService.charger()).thenReturn(ReglagesNotification.depuis(valeurs));
    }

    private static Utilisateur utilisateur(String profil, String email) {
        return Utilisateur.builder().id(7L).email(email).profil(Profil.builder().code(profil).build()).build();
    }

    // Internal users (D1)

    @Test
    void interne_parDefaut_ne_recoit_que_Outlook_a_son_adresse() {
        regler(reglagesInitiaux());

        List<Route> routes = routage.pourUtilisateur(TypeAlerte.RAPPEL_AUDIENCE,
                utilisateur("JURISTE", "juriste.test@afrilandfirstbank.com"));

        assertThat(routes).containsExactly(
                new Route("OUTLOOK", CanalEngine.EMAIL, "juriste.test@afrilandfirstbank.com", null));
    }

    @Test
    void interne_Teams_allume_part_vers_la_meme_adresse() {
        Map<String, String> valeurs = reglagesInitiaux();
        valeurs.put("CANAUX_NOTIFICATION_DEFAUT", "APPLICATIF,OUTLOOK,TEAMS");
        regler(valeurs);

        List<Route> routes = routage.pourUtilisateur(TypeAlerte.DOCUMENT_CHARGE, utilisateur("DJ", "dj@afriland.cm"));

        assertThat(routes).extracting(Route::reglage, Route::canal)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("OUTLOOK", CanalEngine.EMAIL),
                        org.assertj.core.groups.Tuple.tuple("TEAMS", CanalEngine.TEAMS));
    }

    @Test
    void interne_un_type_retire_de_la_grille_ne_part_pas_sur_ce_canal() {
        Map<String, String> valeurs = reglagesInitiaux();
        valeurs.put("GRILLE_NOTIFICATION_OUTLOOK", "DELAI_RECOURS");
        regler(valeurs);

        assertThat(routage.pourUtilisateur(TypeAlerte.RAPPEL_AUDIENCE, utilisateur("JURISTE", "j@afriland.cm"))).isEmpty();
        assertThat(routage.pourUtilisateur(TypeAlerte.DELAI_RECOURS, utilisateur("JURISTE", "j@afriland.cm"))).hasSize(1);
    }

    @Test
    void interne_sans_adresse_reste_visible_comme_non_joignable() {
        regler(reglagesInitiaux());

        assertThat(routage.pourUtilisateur(TypeAlerte.ALARME, utilisateur("SH", null)))
                .containsExactly(new Route("OUTLOOK", CanalEngine.EMAIL, null, MotifNonJoignable.COORDONNEE_ABSENTE));
        assertThat(routage.pourUtilisateur(TypeAlerte.ALARME, utilisateur("SH", "pas-une-adresse")))
                .containsExactly(new Route("OUTLOOK", CanalEngine.EMAIL, null, MotifNonJoignable.COORDONNEE_INVALIDE));
    }

    /** D2: a lawyer with an account follows the provider grid, never Outlook or Teams. */
    @Test
    void un_avocat_avec_compte_n_a_aucun_canal_interne() {
        assertThat(routage.pourUtilisateur(TypeAlerte.PAIEMENT_EFFECTUE, utilisateur("AVOCAT", "avocat@cabinet.cm"))).isEmpty();
        verifyNoInteractions(reglagesNotificationService);
    }

    // Providers (D2, D14, D15)

    @Test
    void prestataire_parDefaut_recoit_courriel_personnel_et_SMS_mais_pas_WhatsApp() {
        regler(reglagesInitiaux());

        List<Route> routes = routage.pourPrestataire(EvenementPrestataire.AFFECTATION_DOSSIER,
                new CoordonneesPrestataire("prestataire.test@example.cm", "600 00 00 00", true));

        assertThat(routes).containsExactly(
                new Route("COURRIEL_PERSONNEL", CanalEngine.EMAIL, "prestataire.test@example.cm", null),
                new Route("SMS", CanalEngine.SMS, "+237600000000", null));
    }

    @Test
    void prestataire_WhatsApp_allume_part_vers_le_meme_numero() {
        Map<String, String> valeurs = reglagesInitiaux();
        valeurs.put("CANAUX_NOTIFICATION_PRESTATAIRES", "WHATSAPP");
        regler(valeurs);

        assertThat(routage.pourPrestataire(EvenementPrestataire.RAPPEL_AUDIENCE,
                new CoordonneesPrestataire(null, "+237600000000", true)))
                .containsExactly(new Route("WHATSAPP", CanalEngine.WHATSAPP, "+237600000000", null));
    }

    /** D14: the channel without contact details is flagged, the others are sent. */
    @Test
    void prestataire_sans_telephone_garde_le_courriel_et_signale_le_SMS() {
        regler(reglagesInitiaux());

        assertThat(routage.pourPrestataire(EvenementPrestataire.FRAIS_REJETES,
                new CoordonneesPrestataire("notaire@etude.cm", "", true)))
                .containsExactly(
                        new Route("COURRIEL_PERSONNEL", CanalEngine.EMAIL, "notaire@etude.cm", null),
                        new Route("SMS", CanalEngine.SMS, null, MotifNonJoignable.COORDONNEE_ABSENTE));
    }

    @Test
    void prestataire_au_numero_hors_format_n_est_pas_devine() {
        regler(reglagesInitiaux());

        assertThat(routage.pourPrestataire(EvenementPrestataire.PAIEMENT_EFFECTUE,
                new CoordonneesPrestataire(null, "0600000000", true)))
                .containsExactly(
                        new Route("COURRIEL_PERSONNEL", CanalEngine.EMAIL, null, MotifNonJoignable.COORDONNEE_ABSENTE),
                        new Route("SMS", CanalEngine.SMS, null, MotifNonJoignable.COORDONNEE_INVALIDE));
    }

    /** D15: without consent, nothing leaves the application, not even an unreachable line. */
    @Test
    void prestataire_sans_consentement_n_est_pas_notifie() {
        assertThat(routage.pourPrestataire(EvenementPrestataire.CONSTITUTION_VALIDEE,
                new CoordonneesPrestataire("prestataire.test@example.cm", "600000000", false))).isEmpty();
        verifyNoInteractions(reglagesNotificationService);
    }

    @Test
    void tous_canaux_prestataires_coupes_rien_ne_part() {
        Map<String, String> valeurs = reglagesInitiaux();
        valeurs.put("CANAUX_NOTIFICATION_PRESTATAIRES", "");
        regler(valeurs);

        assertThat(routage.pourPrestataire(EvenementPrestataire.CONSTITUTION_VALIDEE,
                new CoordonneesPrestataire("prestataire.test@example.cm", "600000000", true))).isEmpty();
    }

    // Reading settings

    @Test
    void reglages_une_cle_absente_ou_une_valeur_inconnue_ne_font_rien_partir() {
        ReglagesNotification reglages = ReglagesNotification.depuis(Map.of(
                "CANAUX_NOTIFICATION_DEFAUT", "APPLICATIF,OUTLOOK,PIGEON",
                "GRILLE_NOTIFICATION_OUTLOOK", "RAPPEL_AUDIENCE, inconnu "));

        assertThat(reglages.canauxInternes()).containsExactlyInAnyOrder(CanalAlerte.APPLICATIF, CanalAlerte.OUTLOOK);
        assertThat(reglages.envoie(CanalAlerte.OUTLOOK, TypeAlerte.RAPPEL_AUDIENCE)).isTrue();
        assertThat(reglages.envoie(CanalAlerte.TEAMS, TypeAlerte.RAPPEL_AUDIENCE)).isFalse();
        assertThat(reglages.envoie(CanalPrestataire.SMS, EvenementPrestataire.RAPPEL_AUDIENCE)).isFalse();
    }

    @Test
    void reglages_la_grille_seule_ne_suffit_pas_il_faut_l_interrupteur() {
        ReglagesNotification reglages = ReglagesNotification.depuis(Map.of(
                "CANAUX_NOTIFICATION_PRESTATAIRES", "SMS",
                "GRILLE_NOTIFICATION_WHATSAPP", TOUS_EVENEMENTS,
                "GRILLE_NOTIFICATION_SMS", ""));

        assertThat(reglages.envoie(CanalPrestataire.WHATSAPP, EvenementPrestataire.RAPPEL_AUDIENCE)).isFalse();
        assertThat(reglages.envoie(CanalPrestataire.SMS, EvenementPrestataire.RAPPEL_AUDIENCE)).isFalse();
    }
}
