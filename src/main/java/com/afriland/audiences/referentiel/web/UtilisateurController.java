package com.afriland.audiences.referentiel.web;

import com.afriland.audiences.referentiel.service.UtilisateurService;
import com.afriland.audiences.referentiel.web.dto.UtilisateurResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M15 (Q-69) — répertoire des utilisateurs internes, endpoint 🆕.
 * <p>
 * {@code ROLE_CONSULTATION} : porté par les six profils internes, il n'est <b>pas</b> attribué au
 * profil {@code AVOCAT} — un intervenant externe ne doit pas pouvoir énumérer le personnel de la
 * Direction Juridique. Même niveau d'habilitation que {@code GET /intervenants}.
 */
@RestController
@RequestMapping("${api.prefix}/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public List<UtilisateurResponse> lister(
            @RequestParam(required = false) String profil,
            @RequestParam(defaultValue = "false") boolean inclureInactifs) {
        return utilisateurService.lister(profil, inclureInactifs);
    }
}
