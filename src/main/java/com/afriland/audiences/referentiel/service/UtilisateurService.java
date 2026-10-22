package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.web.dto.UtilisateurResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Read access to the internal users reference list.
 * <p>
 * Needed by {@code POST /dossiers}, whose mandatory {@code juristesAffectes} field expects
 * {@code utilisateur.id} values. Counterpart of {@code GET /intervenants} for internal staff.
 * <p>
 * <b>Read-only.</b> Accounts are created by provisioning on the first authenticated call
 * ({@link com.afriland.audiences.common.security.UtilisateurProvisioningService}) from Keycloak,
 * which remains the source of truth for identities (REF-05 §1.3): no creation, update or
 * deactivation is exposed here.
 */
@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;

    /**
     * Lists users, filterable by profile code ("JURISTE", "DJ"...).
     *
     * @param profil profile code, or {@code null} for all profiles
     * @param inclureInactifs {@code true} to include deactivated accounts; by default only active
     *        accounts, the only ones assignable to a case file, are listed
     */
    @Transactional(readOnly = true)
    public List<UtilisateurResponse> lister(String profil, boolean inclureInactifs) {
        return utilisateurRepository.findAll().stream()
                .filter(u -> inclureInactifs || Boolean.TRUE.equals(u.getActif()))
                .filter(u -> profil == null || (u.getProfil() != null && profil.equalsIgnoreCase(u.getProfil().getCode())))
                .sorted(Comparator.comparing(Utilisateur::getNom, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Utilisateur::getPrenom, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toResponse)
                .toList();
    }

    private UtilisateurResponse toResponse(Utilisateur utilisateur) {
        return new UtilisateurResponse(
                utilisateur.getId(),
                utilisateur.getNom(),
                utilisateur.getPrenom(),
                utilisateur.getEmail(),
                utilisateur.getProfil() != null ? utilisateur.getProfil().getCode() : null,
                utilisateur.getActif());
    }
}
