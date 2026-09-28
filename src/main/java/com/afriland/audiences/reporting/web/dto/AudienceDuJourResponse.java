package com.afriland.audiences.reporting.web.dto;

import com.afriland.audiences.audience.domain.StatutAudience;

import java.util.List;

/**
 * Une audience d'une journée donnée, avec ce qui en a été rapporté (écran 44).
 *
 * <p><b>Les deux comptes rendus, côte à côte.</b> Celui du juriste, saisi sur l'audience, et celui
 * de l'avocat, déposé en publication puis validé. Une audience peut en porter deux, un seul ou
 * aucun : c'est leur écart qui renseigne sur ce qui reste à faire, et élire l'un des deux ferait
 * passer pour un manquement une audience dont l'autre partie a déjà rendu compte.
 *
 * <p><b>Ce qu'il ne dit pas.</b> Il nomme les juristes <b>affectés au dossier</b>, non un
 * responsable de l'audience — cette notion n'existe pas dans le modèle.
 *
 * @param juristesAffectes    identifiants des juristes du dossier ; l'interface les nomme
 * @param compteRenduJuriste  texte saisi sur l'audience, ou {@code null} s'il manque
 * @param compteRenduAvocat   récit déposé par l'avocat <b>et validé</b>, ou {@code null}. Un dépôt
 *                            non encore validé n'y figure pas : il n'est lisible d'aucun juriste,
 *                            et le compter comme rendu ferait croire à une information disponible
 * @param avocatDuCompteRendu l'avocat qui a déposé, quand il y a un compte rendu d'avocat
 * @param urlPieceJointeAvocat adresse de téléchargement de la pièce accompagnant le compte rendu de
 *                             l'avocat — le plumitif, une note d'audience —, ou {@code null} quand
 *                             il n'en a pas joint. Le compte rendu du juriste, lui, est un texte :
 *                             il n'a jamais de pièce
 */
public record AudienceDuJourResponse(
        Long audienceId,
        Long dossierId,
        String referenceDossier,
        String juridiction,
        String etape,
        StatutAudience statut,
        List<Long> juristesAffectes,
        String compteRenduJuriste,
        String compteRenduAvocat,
        Long avocatDuCompteRendu,
        String urlPieceJointeAvocat) {

    /** Vrai quand rien n'a été rapporté de cette audience, d'aucun côté. */
    public boolean sansAucunCompteRendu() {
        return compteRenduJuriste == null && compteRenduAvocat == null;
    }
}
