package com.afriland.audiences.notification.service;

import java.util.Optional;

/**
 * Référence d'un dossier, pour l'objet et le corps des notifications externes. Interface déclarée ici
 * et implémentée par le module dossier : le module notification ne dépend pas du module dossier, qui
 * l'appellera lui-même pour notifier les prestataires (étape 7).
 */
public interface ReferencesDossier {

    Optional<String> reference(Long dossierId);
}
