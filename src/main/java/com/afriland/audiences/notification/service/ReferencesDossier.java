package com.afriland.audiences.notification.service;

import java.util.Optional;

/**
 * Case-file reference used in the subject and body of external notifications. Declared here and
 * implemented by the dossier module so that the notification module does not depend on it.
 */
public interface ReferencesDossier {

    Optional<String> reference(Long dossierId);
}
