package com.afriland.audiences.referentiel.web.dto;

import java.time.LocalDateTime;

/**
 * L'état de la signature enregistrée de l'utilisateur courant (N8).
 *
 * <p>Ne porte <b>pas l'image</b> : l'écran a besoin de savoir s'il propose un dépôt ou un
 * remplacement, pas d'afficher le trait. L'image a son propre appel, pour qu'elle ne voyage pas à
 * chaque chargement.
 */
public record SignatureResponse(boolean deposee, String type, LocalDateTime deposeeLe) {
}
