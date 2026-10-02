package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.notification.service.ReferencesDossier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Fournit au module notification la référence d'un dossier, sans charger le dossier entier. */
@Component
@RequiredArgsConstructor
public class ReferencesDossierJpa implements ReferencesDossier {

    private final DossierRepository dossierRepository;

    @Override
    public Optional<String> reference(Long dossierId) {
        return dossierId == null ? Optional.empty() : dossierRepository.findReferenceById(dossierId);
    }
}
