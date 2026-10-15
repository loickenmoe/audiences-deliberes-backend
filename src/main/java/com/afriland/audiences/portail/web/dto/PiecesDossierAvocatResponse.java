package com.afriland.audiences.portail.web.dto;

import java.util.List;

/**
 * Les pièces partagées d'un dossier, et la raison de leur absence (QF-64).
 *
 * <p>{@code constitue} est porté à part, plutôt que de renvoyer une liste vide : « aucune pièce » et
 * « pas encore constitué » sont deux situations que l'avocat doit distinguer. La seconde se règle
 * par la signature d'une lettre, la première par une demande au juriste.
 */
public record PiecesDossierAvocatResponse(boolean constitue, List<PieceAvocatResponse> pieces) {
}
