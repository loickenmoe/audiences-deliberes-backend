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
 * La signature enregistrée d'un utilisateur interne (métier, 2026-10-03, observation 10 ;
 * provenance arrêtée le 2026-10-06).
 *
 * <p><b>Chacun dépose la sienne, et personne d'autre.</b> Le service ne prend jamais d'identifiant
 * en paramètre : il travaille sur l'utilisateur de la session. C'est la seule version où personne ne
 * peut apposer la signature d'un autre - et une signature qu'un tiers détient ne vaut déjà plus
 * rien.
 *
 * <p><b>Ce que cette image est, et ce qu'elle n'est pas.</b> Un confort de présentation : la lettre
 * de constitution part avec l'image au lieu d'un nom à signer à la main. Elle n'apporte
 * <b>aucune valeur probante supplémentaire</b> - ce qui fait preuve, c'est la trace horodatée de qui
 * a signé, que l'application enregistre déjà. Il a paru honnête de le dire plutôt que de laisser
 * croire le contraire.
 */
@Service
@RequiredArgsConstructor
public class SignatureService {

    /**
     * Les deux seuls types que le PDF sait apposer.
     *
     * <p>PDFBox lit le PNG et le JPEG ; un SVG ou un PDF déposé ici produirait une lettre sans
     * signature, et le défaut ne se verrait qu'à l'impression. Le refus vaut mieux.
     */
    private static final List<String> TYPES_ADMIS = List.of("image/png", "image/jpeg");

    /**
     * Deux cent mille octets : large pour une signature scannée, étroit pour tout le reste.
     *
     * <p>La borne n'est pas dans {@code configuration_systeme} comme {@code TAILLE_MAX_GED} : ce
     * n'est pas un réglage d'exploitation, c'est ce qui distingue une signature d'une photographie.
     * Une image plus lourde est le signe qu'on s'est trompé de fichier.
     */
    private static final long TAILLE_MAX = 200_000L;

    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserService currentUserService;

    /** La signature de l'utilisateur de la session, si elle a été déposée. */
    @Transactional(readOnly = true)
    public Optional<SignatureDeposee> mienne() {
        Utilisateur moi = utilisateurRepository.findById(currentUserService.getCurrentUser().getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Utilisateur introuvable"));
        return lire(moi);
    }

    /**
     * La signature d'un utilisateur donné, pour l'apposer sur un document qu'il a signé.
     *
     * <p>Réservé aux appels internes - aucun endpoint ne l'expose. Un endpoint rendant la signature
     * d'autrui permettrait de la récupérer pour l'apposer ailleurs, ce qui est exactement ce que le
     * dépôt réservé au titulaire cherche à empêcher.
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
     * Retire sa signature.
     *
     * <p>Les lettres déjà signées ne changent pas : leur PDF est produit et archivé en GED. Retirer
     * l'image n'efface donc rien de ce qui est parti - et n'efface pas non plus la preuve, qui n'a
     * jamais été l'image.
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

    /** Une signature déposée : son image, son type et la date de son dépôt. */
    public record SignatureDeposee(byte[] image, String type, LocalDateTime deposeeLe) {
    }
}
