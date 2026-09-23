package com.afriland.audiences.notification.config;

import com.afriland.audiences.notification.config.NotificationEngineProperties.Redirection;
import com.afriland.audiences.notification.passerelle.PasserelleJournal;
import com.afriland.audiences.notification.passerelle.PasserelleNotificationEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEngineConfigTest {

    private static final String SECRET_32 = "0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(NotificationEngineConfig.class);

    @Test
    void sans_configuration_l_engine_est_desactive_et_rien_n_est_envoye() {
        contexte.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(NotificationEngineProperties.class).actif()).isFalse();
            assertThat(ctx.getBean(PasserelleNotificationEngine.class)).isInstanceOf(PasserelleJournal.class);
        });
    }

    @Test
    void desactive_le_compte_de_service_n_est_pas_exige() {
        contexte.withPropertyValues("notification.engine.actif=false", "notification.engine.jwt-secret=court")
                .run(ctx -> assertThat(ctx.getBean(PasserelleNotificationEngine.class)).isInstanceOf(PasserelleJournal.class));
    }

    @Test
    void actif_sans_code_service_le_demarrage_echoue_explicitement() {
        contexte.withPropertyValues("notification.engine.actif=true", "notification.engine.jwt-secret=" + SECRET_32)
                .run(ctx -> assertThat(ctx).getFailure().rootCause().hasMessageContaining("NOTIFICATION_ENGINE_CODE_SERVICE"));
    }

    @Test
    void actif_avec_un_secret_trop_court_le_demarrage_echoue_explicitement() {
        contexte.withPropertyValues("notification.engine.actif=true", "notification.engine.code-service=audience-deliberer",
                        "notification.engine.jwt-secret=" + SECRET_32.substring(1))
                .run(ctx -> assertThat(ctx).getFailure().rootCause().hasMessageContaining("au moins 32 caractères"));
    }

    @Test
    void actif_avec_un_compte_complet_le_demarrage_echoue_tant_qu_aucun_transport_n_existe() {
        contexte.withPropertyValues("notification.engine.actif=true", "notification.engine.code-service=audience-deliberer",
                        "notification.engine.jwt-secret=" + SECRET_32, "notification.engine.redirection.actif=false")
                .run(ctx -> assertThat(ctx).getFailure().rootCause().hasMessageContaining("aucun transport réel"));
    }

    /**
     * D10 : hors production, une redirection incomplète laisserait partir un message vers un vrai
     * avocat. Le démarrage échoue plutôt que d'en prendre le risque.
     */
    @Test
    void actif_avec_une_redirection_incomplete_le_demarrage_echoue() {
        contexte.withPropertyValues("notification.engine.actif=true", "notification.engine.code-service=audience-deliberer",
                        "notification.engine.jwt-secret=" + SECRET_32,
                        "notification.engine.redirection.courriel-interne=paul_fokam@afrilandfirstbank.com")
                .run(ctx -> assertThat(ctx).getFailure().rootCause()
                        .hasMessageContaining("Redirection active mais incomplète"));
    }

    @Test
    void la_redirection_est_active_par_defaut() {
        contexte.run(ctx -> assertThat(ctx.getBean(NotificationEngineProperties.class).redirection().actif()).isTrue());
    }

    @Test
    void le_secret_n_apparait_jamais_dans_la_representation_texte() {
        NotificationEngineProperties proprietes = new NotificationEngineProperties(true, "audience-deliberer",
                SECRET_32, 5, java.time.Duration.ofHours(2), new Redirection(false, null, null, null));

        assertThat(proprietes.toString()).doesNotContain(SECRET_32).contains("jwtSecret=***");
        assertThat(new NotificationEngineProperties(false, null, null, 5, java.time.Duration.ofHours(2),
                new Redirection(false, null, null, null)).toString()).contains("jwtSecret=(absent)");
    }
}
