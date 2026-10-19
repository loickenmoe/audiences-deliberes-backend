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
 * Directory of internal users, and each user's <b>stored signature</b>.
 * <p>
 * {@code ROLE_CONSULTATION} is carried by the six internal profiles and <b>not</b> by
 * {@code AVOCAT}: an external party must not be able to list Legal Department staff. Same
 * authorisation level as {@code GET /intervenants}.
 * <p>
 * <b>Signature endpoints only act on "my" signature</b>: they work on the session user and never
 * take an id, so none can read or set someone else's signature. The appointment letter applies the
 * signer's signature through an internal service call.
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
     * State of my signature: uploaded or not, and since when.
     *
     * <p>Returns the state, <b>not the image</b>: a separate endpoint serves the image so it is not
     * sent on every screen load.
     */
    @GetMapping("/moi/signature")
    @PreAuthorize("isAuthenticated()")
    public SignatureResponse maSignature() {
        return signatureService.mienne()
                .map(signature -> new SignatureResponse(true, signature.type(), signature.deposeeLe()))
                .orElseGet(() -> new SignatureResponse(false, null, null));
    }

    /** The image itself, for preview. 404 until one is uploaded. */
    @GetMapping("/moi/signature/image")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> maSignatureImage() {
        return signatureService.mienne()
                .map(signature -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(signature.type()))
                        .body(signature.image()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Uploads or replaces my signature. PNG or JPEG, 200 KB max. */
    @PutMapping("/moi/signature")
    @PreAuthorize("isAuthenticated()")
    public SignatureResponse deposerMaSignature(@RequestParam("fichier") MultipartFile fichier) {
        var signature = signatureService.deposer(fichier);
        return new SignatureResponse(true, signature.type(), signature.deposeeLe());
    }

    /**
     * Removes my signature.
     *
     * <p>Already signed letters are unchanged: their PDF is generated and archived, and the evidence of
     * signing was never this image.
     */
    @DeleteMapping("/moi/signature")
    @PreAuthorize("isAuthenticated()")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirerMaSignature() {
        signatureService.retirer();
    }
}
