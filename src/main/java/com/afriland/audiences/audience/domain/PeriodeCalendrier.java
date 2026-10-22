package com.afriland.audiences.audience.domain;

import java.time.LocalDate;

/**
 * FR-AUD-05: hearing calendar window, relative to a given {@code dateDebut}.
 * <p>
 * <b>The end bound is inclusive</b> (the query uses {@code BETWEEN}), so a period ends the <b>day
 * before</b> the next one; otherwise the boundary day would be counted in two consecutive periods.
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
