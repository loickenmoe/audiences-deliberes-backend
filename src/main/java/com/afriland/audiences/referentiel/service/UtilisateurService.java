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
 * M15 (Q-69) — lecture du référentiel des utilisateurs internes.
 * <p>
 * Sans elle, {@code POST /dossiers} était inutilisable depuis une interface : son champ
 * {@code juristesAffectes} est obligatoire ({@code @NotEmpty}) et attend des {@code utilisateur.id},
 * qu'aucun endpoint ne permettait d'obtenir. Pendant de {@code GET /intervenants} pour le personnel
 * interne.
 * <p>
 * <b>Lecture seule.</b> Les comptes naissent du provisionnement au premier appel authentifié
 * ({@link com.afriland.audiences.common.security.UtilisateurProvisioningService}) à partir de
 * Keycloak, qui reste la source de vérité des identités (REF-05 §1.3) : ni création, ni
 * modification, ni désactivation ne sont exposées ici.
 */
@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;

    /**
     * Liste les utilisateurs, filtrable par code de profil (« JURISTE », « DJ »…).
     *
     * @param profil code de profil, ou {@code null} pour tous les profils
     * @param inclureInactifs {@code true} pour inclure les comptes désactivés — par défaut la
     *        liste ne sert que des comptes actifs, seuls affectables à un dossier
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
