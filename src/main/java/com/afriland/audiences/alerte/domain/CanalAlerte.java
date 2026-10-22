package com.afriland.audiences.alerte.domain;

/**
 * Alert delivery channel (RG-ALR-03). {@code APPLICATIF} is the only channel delivered by this
 * module; Outlook and Teams go through the external notification module.
 */
public enum CanalAlerte {
    APPLICATIF,
    OUTLOOK,
    TEAMS
}
