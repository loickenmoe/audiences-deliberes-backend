package com.afriland.audiences.audience.domain;

import java.time.LocalDate;

/**
 * FR-AUD-05 : fenêtre du calendrier des audiences, relative à un {@code dateDebut} donné.
 * <p>
 * <b>La borne de fin est incluse</b> (la requête interroge {@code BETWEEN}) : une période s'arrête
 * donc la <b>veille</b> de la suivante. Sans ce retrait d'un jour, une semaine commençant un lundi
 * ramenait aussi le lundi suivant, compté deux fois entre deux périodes consécutives (QF-31).
 */
public enum PeriodeCalendrier {
    HEBDOMADAIRE {
        public LocalDate dateFin(LocalDate debut) {
            return debut.plusWeeks(1).minusDays(1);
        }
    },
    MENSUELLE {
        public LocalDate dateFin(LocalDate debut) {
            return debut.plusMonths(1).minusDays(1);
        }
    },
    TRIMESTRIELLE {
        public LocalDate dateFin(LocalDate debut) {
            return debut.plusMonths(3).minusDays(1);
        }
    };

    public abstract LocalDate dateFin(LocalDate debut);
}
