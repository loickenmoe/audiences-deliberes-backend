package com.afriland.audiences.alerte.domain;

/**
 * Canal de diffusion d'une alerte (RG-ALR-03). {@code APPLICATIF} est le seul canal effectivement
 * délivré par ce module en M5 — cf. Javadoc de {@link com.afriland.audiences.alerte.service.AlerteService}
 * et DECISIONS_AND_OPEN_QUESTIONS.md Q-37 (Outlook/Teams différés, aucune infrastructure
 * SMTP/webhook fournie au projet).
 */
public enum CanalAlerte {
    APPLICATIF,
    OUTLOOK,
    TEAMS
}
