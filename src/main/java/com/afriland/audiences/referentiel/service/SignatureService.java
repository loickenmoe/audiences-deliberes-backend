package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The stored signature of an internal user.
 *
 * <p><b>Each user uploads their own, and nobody else's.</b> The service never takes a user id as a
 * parameter: it works on the session user, so nobody can apply someone else's signature.
 *
 * <p><b>A presentation convenience</b>: the appointment letter carries the image instead of a name
 * to sign by hand. It adds <b>no evidential value</b>: evidence is the timestamped record of who
 * signed, which the application already keeps.
 */
@Service
@RequiredArgsConstructor
public class SignatureService {

    /**
     * The only two types the PDF can embed.
     *
     * <p>PDFBox reads PNG and JPEG; an SVG or PDF uploaded here would produce a letter without a
     * signature, noticed only when printed. Rejecting it is better.
     */
    private static final List<String> TYPES_ADMIS = List.of("image/png", "image/jpeg");

    /**
     * 200,000 bytes: ample for a scanned signature, tight for anything else.
     *
     * <p>Not in {@code configuration_systeme} like {@code TAILLE_MAX_GED}: it is not an operational
     * setting but what distinguishes a signature from a photograph.
     */
    private static final long TAILLE_MAX = 200_000L;

    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserService currentUserService;

    /** The session user's signature, if uploaded. */
    @Transactional(readOnly = true)
    public Optional<SignatureDeposee> mienne() {
        Utilisateur moi = utilisateurRepository.findById(currentUserService.getCurrentUser().getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Utilisateur introuvable"));
        return lire(moi);
    }

    /**
     * A given user's signature, to apply it to a document they signed.
     *
     * <p>Internal use only, no endpoint exposes it: an endpoint returning someone else's signature
     * would allow reusing it elsewhere, which is exactly what holder-only upload prevents.
     */
    @Transactional(readOnly = true)
    public Optional<SignatureDeposee> pour(Long utilisateurId) {
        if (utilisateurId == null) {
            return Optional.empty();
        }
        return utilisateurRepository.findById(utilisateurId).flatMap(SignatureService::lire);
    }

    @Transactional
    public SignatureDeposee deposer(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.METADONNEES_MANQUANTES,
                    "Aucun fichier de signature n'a été transmis");
        }
        String type = fichier.getContentType();
        if (type == null || !TYPES_ADMIS.contains(type.toLowerCase())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Une signature se dépose en PNG ou en JPEG : ce sont les deux formats que la "
                            + "lettre sait apposer");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Cette image dépasse 200 Ko : une signature scannée tient largement en dessous");
        }

        Utilisateur moi = utilisateurRepository.findById(currentUserService.getCurrentUser().getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Utilisateur introuvable"));
        try {
            moi.setSignature(fichier.getBytes());
        } catch (java.io.IOException erreur) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Le fichier de signature n'a pas pu être lu");
        }
        moi.setSignatureType(type.toLowerCase());
        moi.setSignatureDeposeeLe(LocalDateTime.now());
        utilisateurRepository.save(moi);
        return new SignatureDeposee(moi.getSignature(), moi.getSignatureType(), moi.getSignatureDeposeeLe());
    }

    /**
     * Removes the user's signature.
     *
     * <p>Already signed letters are unchanged: their PDF is generated and archived in the GED.
     * Removing the image erases nothing already sent, nor the evidence, which was never the image.
     */
    @Transactional
    public void retirer() {
        Utilisateur moi = utilisateurRepository.findById(currentUserService.getCurrentUser().getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Utilisateur introuvable"));
        moi.setSignature(null);
        moi.setSignatureType(null);
        moi.setSignatureDeposeeLe(null);
        utilisateurRepository.save(moi);
    }

    private static Optional<SignatureDeposee> lire(Utilisateur utilisateur) {
        byte[] image = utilisateur.getSignature();
        if (image == null || image.length == 0) {
            return Optional.empty();
        }
        return Optional.of(new SignatureDeposee(image, utilisateur.getSignatureType(),
                utilisateur.getSignatureDeposeeLe()));
    }

    /** A stored signature: its image, type and upload date. */
    public record SignatureDeposee(byte[] image, String type, LocalDateTime deposeeLe) {
    }
}
