package com.afriland.audiences.notification.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Supervision des notifications externes sur une fenêtre (D17, écran 41).
 *
 * <p><b>Ce que ce tableau dit, et ce qu'il ne dit pas.</b> Il compte ce que l'application a fait —
 * mis en file, remis au moteur, abandonné — et ce que le moteur a confirmé. La confirmation ligne à
 * ligne n'est possible que par le rappel (Q-120) : sans lui, un envoi reste « remis » jusqu'à ce que
 * le délai d'attente le fasse basculer en « sans retour ». D'où la distinction, dans chaque ligne,
 * entre {@code remises} et {@code confirmees}.
 *
 * @param canaux       une ligne par canal, dans l'ordre des canaux du moteur
 * @param derniersEchecs les envois à examiner : échec, sans retour ou destinataire injoignable
 */
public record SupervisionNotificationsResponse(
        LocalDate dateDebut,
        LocalDate dateFin,
        List<LigneCanal> canaux,
        List<EnvoiEnDefaut> derniersEchecs,
        boolean engineActif) {

    /**
     * @param enAttente    en file, pas encore remises au moteur
     * @param remises      remises au moteur, sans confirmation encore reçue
     * @param confirmees   confirmées par un accusé du moteur
     * @param echecs       abandonnées : tentatives épuisées, périmées, ou échec définitif du moteur
     * @param sansRetour   remises, mais restées sans aucun accusé au-delà du délai (Q-120)
     * @param nonJoignables canal retenu pour un destinataire sans coordonnée utilisable (D14)
     */
    public record LigneCanal(String canal, long enAttente, long remises, long confirmees, long echecs,
                             long sansRetour, long nonJoignables) {

        public long total() {
            return enAttente + remises + confirmees + echecs + sansRetour + nonJoignables;
        }

        /**
         * Part de ce qui est parti ou en passe de partir. Les lignes sans coordonnée en sont exclues :
         * elles ne disent rien du canal, seulement d'une fiche incomplète.
         */
        public BigDecimal tauxAboutissement() {
            long denominateur = total() - nonJoignables;
            if (denominateur == 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(confirmees + remises + enAttente)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(denominateur), 1, RoundingMode.HALF_UP);
        }
    }

    /**
     * @param destinataireOrigine destinataire réel quand la redirection hors production l'a remplacé (D10)
     */
    public record EnvoiEnDefaut(Long id, String eventId, String typeEvenement, String canal, String statut,
                                String destinataire, String destinataireOrigine, String derniereErreur,
                                int tentatives, String dateCreation) {
    }
}
