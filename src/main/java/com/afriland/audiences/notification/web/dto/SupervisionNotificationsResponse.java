package com.afriland.audiences.notification.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Monitoring of external notifications over a time window. Counts what the application did (queued,
 * delivered to the engine, abandoned) and what the engine confirmed. Row-level confirmation is only
 * possible through the callback, hence the distinction between {@code remises} and {@code confirmees}.
 *
 * @param canaux       one row per channel, in engine channel order
 * @param derniersEchecs    sends needing review: failed, without receipt or unreachable recipient
 * @param suiviAccusesActif whether the engine publishes receipts; if not, "delivered" is the last known
 *                          state of a successful send and the confirmations column stays empty
 */
public record SupervisionNotificationsResponse(
        LocalDate dateDebut,
        LocalDate dateFin,
        List<LigneCanal> canaux,
        List<EnvoiEnDefaut> derniersEchecs,
        boolean engineActif,
        boolean suiviAccusesActif) {

    /**
     * @param enAttente    queued, not yet delivered to the engine
     * @param remises      delivered to the engine, no confirmation received yet
     * @param confirmees   confirmed by an engine receipt
     * @param echecs       abandoned: attempts exhausted, expired, or permanent engine failure
     * @param sansRetour   delivered but without any receipt past the delay
     * @param nonJoignables channel selected for a recipient without usable contact details
     */
    public record LigneCanal(String canal, long enAttente, long remises, long confirmees, long echecs,
                             long sansRetour, long nonJoignables) {

        public long total() {
            return enAttente + remises + confirmees + echecs + sansRetour + nonJoignables;
        }

        /**
         * Share of what was sent or is about to be. Rows without contact details are excluded: they
         * reflect an incomplete record, not the channel.
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
     * @param destinataireOrigine real recipient when non-production redirection replaced it
     */
    public record EnvoiEnDefaut(Long id, String eventId, String typeEvenement, String canal, String statut,
                                String destinataire, String destinataireOrigine, String derniereErreur,
                                int tentatives, String dateCreation) {
    }
}
