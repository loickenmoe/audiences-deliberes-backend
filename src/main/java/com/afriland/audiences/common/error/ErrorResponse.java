package com.afriland.audiences.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corps de réponse d'erreur, conforme au contrat d'API (REF-07 §1.3) :
 * {@code {code, message, details}}. {@code details} est optionnel (peut être {@code null}).
 * <p>
 * {@code champ} nomme, quand c'est possible, le champ de la requête à corriger. Il a été ajouté
 * parce que {@code details} ne pouvait pas jouer ce rôle : il porte du texte destiné à un humain
 * (« reference: ne doit pas être vide ; nom: ne doit pas être vide »), pas un identifiant
 * exploitable par un client. Sans lui, un client recevant {@code ERR-CONFLICT} — code employé par
 * une dizaine de conflits sans rapport — ne peut pas savoir quel champ signaler à l'utilisateur, et
 * doit le deviner à partir de l'endpoint appelé.
 * <p>
 * L'ajout est rétrocompatible : le champ est absent du JSON lorsqu'il est {@code null}, ce qui
 * laisse inchangées toutes les réponses qui ne visent pas un champ précis.
 */
public record ErrorResponse(
        String code,
        String message,
        String details,
        @JsonInclude(JsonInclude.Include.NON_NULL) String champ) {

    public ErrorResponse(String code, String message) {
        this(code, message, null, null);
    }

    public ErrorResponse(String code, String message, String details) {
        this(code, message, details, null);
    }
}
