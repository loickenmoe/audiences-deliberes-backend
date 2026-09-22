package com.afriland.audiences.notification.domain;

import com.afriland.audiences.notification.passerelle.CanalEngine;

/**
 * Canal externe par lequel un prestataire (avocat ou autre prestataire) est notifié (D2). Les
 * utilisateurs internes, eux, passent par Outlook et Teams ({@link com.afriland.audiences.alerte.domain.CanalAlerte}).
 * <p>
 * Le courriel personnel et Outlook aboutissent tous deux au canal {@link CanalEngine#EMAIL} du
 * moteur ; ils restent distincts ici parce que le DJ les règle séparément, pour des publics différents.
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
