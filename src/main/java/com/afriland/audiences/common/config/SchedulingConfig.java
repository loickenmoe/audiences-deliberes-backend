package com.afriland.audiences.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} methods (hearing reminders D-7/D-3/D-1, alerts, 72-hour reminders for
 * document deletion requests).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
