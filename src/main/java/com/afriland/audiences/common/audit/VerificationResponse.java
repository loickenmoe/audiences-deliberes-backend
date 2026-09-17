package com.afriland.audiences.common.audit;

/**
 * Résultat d'une vérification d'empreinte (Q-114).
 *
 * <p><b>Un fichier inconnu n'est pas une erreur</b> : c'est une réponse, et souvent celle qu'on
 * redoutait — le document qu'on vous a remis ne correspond à aucune archive. La renvoyer en 404
 * l'aurait fait passer pour une panne de l'application.
 *
 * @param reconnue vrai si l'empreinte correspond à une archive produite par cette application
 * @param archive  l'archive correspondante, ou {@code null} si aucune
 */
public record VerificationResponse(boolean reconnue, ExportAuditResponse archive) {
}
