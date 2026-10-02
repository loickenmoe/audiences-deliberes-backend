package com.afriland.audiences.alerte.domain;

import com.afriland.audiences.referentiel.domain.Utilisateur;

/**
 * Publié par {@code AlerteService.declencher} une fois l'alerte enregistrée, <b>dans la transaction de
 * l'action métier</b>. Les modules qui prolongent une alerte (les notifications externes, étape 6 de
 * NOTIFICATION_ENGINE_PLAN.md) l'écoutent sans que le module alerte ait à les connaître : il reste le
 * socle transversal décrit dans la Javadoc d'{@link Alerte}.
 * <p>
 * Le texte {@code declencheur} n'y figure pas à dessein : écrit pour l'écran, il peut contenir un
 * motif, un nom ou un marqueur technique, et n'a pas à circuler au-delà du canal applicatif.
 */
public record AlerteDeclenchee(Long alerteId, TypeAlerte type, Long dossierId, Utilisateur destinataire) {
}
