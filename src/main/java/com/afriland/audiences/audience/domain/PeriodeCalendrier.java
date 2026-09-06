package com.afriland.audiences.audience.domain;

import java.time.LocalDate;

/** FR-AUD-05 : fenêtre du calendrier des audiences, relative à un {@code dateDebut} donné. */
public enum PeriodeCalendrier {
    HEBDOMADAIRE {
        public LocalDate dateFin(LocalDate debut) {
            return debut.plusWeeks(1);
        }
    },
    MENSUELLE {
        public LocalDate dateFin(LocalDate debut) {
            return debut.plusMonths(1);
        }
    },
    TRIMESTRIELLE {
        public LocalDate dateFin(LocalDate debut) {
            return debut.plusMonths(3);
        }
    };

    public abstract LocalDate dateFin(LocalDate debut);
}
