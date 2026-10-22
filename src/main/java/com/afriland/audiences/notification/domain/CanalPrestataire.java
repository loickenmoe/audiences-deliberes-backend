package com.afriland.audiences.notification.domain;

import com.afriland.audiences.notification.passerelle.CanalEngine;

/**
 * External channel used to notify a provider (lawyer or other). Internal users go through Outlook and
 * Teams ({@link com.afriland.audiences.alerte.domain.CanalAlerte}).
 * <p>
 * Personal e-mail and Outlook both map to the engine's {@link CanalEngine#EMAIL} channel; they stay
 * distinct here because they are configured separately, for different audiences.
 */
public enum CanalPrestataire {
    COURRIEL_PERSONNEL(CanalEngine.EMAIL),
    SMS(CanalEngine.SMS),
    WHATSAPP(CanalEngine.WHATSAPP);

    private final CanalEngine canalEngine;

    CanalPrestataire(CanalEngine canalEngine) {
        this.canalEngine = canalEngine;
    }

    public CanalEngine canalEngine() {
        return canalEngine;
    }
}
