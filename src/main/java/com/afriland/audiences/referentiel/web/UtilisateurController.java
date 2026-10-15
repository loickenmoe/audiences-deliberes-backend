package com.afriland.audiences.referentiel.web;

import com.afriland.audiences.referentiel.service.SignatureService;
import com.afriland.audiences.referentiel.service.UtilisateurService;
import com.afriland.audiences.referentiel.web.dto.SignatureResponse;
import com.afriland.audiences.referentiel.web.dto.UtilisateurResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * M15 (Q-69) — répertoire des utilisateurs internes, endpoint 🆕, et la <b>signature
 * enregistrée</b> de chacun (N8).
 * <p>
 * {@code ROLE_CONSULTATION} : porté par les six profils internes, il n'est <b>pas</b> attribué au
 * profil {@code AVOCAT} — un intervenant externe ne doit pas pouvoir énumérer le personnel de la
 * Direction Juridique. Même niveau d'habilitation que {@code GET /intervenants}.
 * <p>
 * <b>La signature se dit « mienne », et jamais autrement.</b> Les trois endpoints de signature
 * travaillent sur l'utilisateur de la session, sans jamais prendre d'identifiant : aucun ne permet
 * de lire ou de poser la signature d'un autre (métier, 2026-10-06). C'est la seule forme où une
 * signature garde un sens - celle qu'un tiers détient ne vaut déjà plus rien. La lettre de
 * constitution, elle, appose celle du signataire par un appel interne au service.
 */
@RestController
@RequestMapping("${api.prefix}/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;
    private final SignatureService signatureService;

    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public List<UtilisateurResponse> lister(
            @RequestParam(required = false) String profil,
            @RequestParam(defaultValue = "false") boolean inclureInactifs) {
        return utilisateurService.lister(profil, inclureInactifs);
    }

    /**
     * L'état de ma signature : déposée ou non, et depuis quand.
     *
     * <p>Rend l'état, <b>pas l'image</b> : l'écran a besoin de savoir s'il doit proposer un dépôt ou
     * un remplacement, pas d'afficher le trait. Un appel distinct sert l'image, pour que celle-ci ne
     * voyage pas à chaque chargement d'écran.
     */
    @GetMapping("/moi/signature")
    @PreAuthorize("isAuthenticated()")
    public SignatureResponse maSignature() {
        return signatureService.mienne()
                .map(signature -> new SignatureResponse(true, signature.type(), signature.deposeeLe()))
                .orElseGet(() -> new SignatureResponse(false, null, null));
    }

    /** L'image elle-même, pour l'aperçu. 404 tant que rien n'est déposé. */
    @GetMapping("/moi/signature/image")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> maSignatureImage() {
        return signatureService.mienne()
                .map(signature -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(signature.type()))
                        .body(signature.image()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Dépose ou remplace ma signature. PNG ou JPEG, 200 Ko au plus. */
    @PutMapping("/moi/signature")
    @PreAuthorize("isAuthenticated()")
    public SignatureResponse deposerMaSignature(@RequestParam("fichier") MultipartFile fichier) {
        var signature = signatureService.deposer(fichier);
        return new SignatureResponse(true, signature.type(), signature.deposeeLe());
    }

    /**
     * Retire ma signature.
     *
     * <p>Les lettres déjà signées ne changent pas : leur PDF est produit et archivé. Et la preuve
     * de la signature n'a jamais été cette image.
     */
    @DeleteMapping("/moi/signature")
    @PreAuthorize("isAuthenticated()")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirerMaSignature() {
        signatureService.retirer();
    }
}
