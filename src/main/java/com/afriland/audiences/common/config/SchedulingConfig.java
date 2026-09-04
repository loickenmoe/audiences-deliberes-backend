package com.afriland.audiences.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active l'exécution des méthodes {@code @Scheduled}, socle des traitements planifiés à venir :
 * déclenchement des rappels d'audience J-7/J-3/J-1 et des alertes (module alerte, M5), relance à
 * 72 h des demandes de suppression de document (module GED, M8).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
