package com.afriland.audiences.common.reference;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Attribue les références que le système remplit lui-même : {@code DOS-2026-0001},
 * {@code CLI-2026-0001} (métier, 2026-09-29).
 *
 * <p><b>Le format.</b> Préfixe, année, numéro sur quatre chiffres, séparés par des tirets. Il
 * reprend celui des archives d'audit ({@code AUD-2026-0042}, V21) plutôt que d'en inventer un
 * nouveau : une référence se dicte au téléphone et s'écrit dans un courrier, et deux conventions
 * dans la même application obligeraient à se demander laquelle on lit.
 *
 * <p><b>Le numéro repart à 1 chaque année</b>, contrairement aux archives d'audit. C'est le seul
 * écart, et il est délibéré : {@code DOS-2027-0158} se lit « le 158e dossier de 2027 ». Avec un
 * compteur global il serait le 158e depuis toujours, et la référence dirait, tous les jours, quelque
 * chose de faux. Le surcoût est nul — l'atomicité vient de la base (V36).
 *
 * <p><b>Un numéro servi n'est jamais rendu.</b> Si la création échoue après l'appel, le numéro reste
 * consommé et la suite présente un trou. C'est le comportement de toute numérotation sérieuse :
 * l'alternative — réutiliser un numéro libéré — ferait qu'une référence citée dans un courrier
 * désignerait plus tard un autre dossier.
 */
@Service
@RequiredArgsConstructor
public class GenerateurReference {

    private final CompteurReferenceRepository compteurReferenceRepository;

    /**
     * La prochaine référence de cette famille, pour l'année en cours.
     *
     * <p><b>{@code REQUIRES_NEW} est essentiel.</b> Sans cela, le numéro serait servi dans la
     * transaction de la création : un dossier refusé plus loin — par une validation, une affectation
     * introuvable — annulerait aussi l'incrément, et le numéro repartirait à un suivant déjà promis
     * à quelqu'un d'autre. Deux créations concurrentes dont l'une échoue produiraient alors deux
     * fois la même référence. Le compteur avance donc pour de bon, dès qu'on lui demande un numéro.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String prochaine(FamilleReference famille) {
        int annee = LocalDate.now().getYear();
        int numero = compteurReferenceRepository.prochainNumero(famille.name(), annee);
        return formater(famille, annee, numero);
    }

    /**
     * Le format, isolé pour être vérifiable sans base.
     *
     * <p>Au-delà de 9999 dossiers dans une même année, le numéro déborde sur cinq chiffres plutôt
     * que de recommencer à zéro : une référence tronquée en désignerait deux. La colonne en accepte
     * cinquante, le cas resterait lisible.
     */
    static String formater(FamilleReference famille, int annee, int numero) {
        return String.format("%s-%d-%04d", famille.prefixe(), annee, numero);
    }
}
