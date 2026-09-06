package com.afriland.audiences.audience.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodeCalendrierTest {

    private final LocalDate debut = LocalDate.of(2026, 1, 1);

    @Test
    void hebdomadaire_addsOneWeek() {
        assertThat(PeriodeCalendrier.HEBDOMADAIRE.dateFin(debut)).isEqualTo(LocalDate.of(2026, 1, 8));
    }

    @Test
    void mensuelle_addsOneMonth() {
        assertThat(PeriodeCalendrier.MENSUELLE.dateFin(debut)).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void trimestrielle_addsThreeMonths() {
        assertThat(PeriodeCalendrier.TRIMESTRIELLE.dateFin(debut)).isEqualTo(LocalDate.of(2026, 4, 1));
    }
}
