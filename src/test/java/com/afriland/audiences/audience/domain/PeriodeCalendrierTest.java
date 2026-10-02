package com.afriland.audiences.audience.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodeCalendrierTest {

    private final LocalDate debut = LocalDate.of(2026, 1, 1);

    /** QF-31 : la borne est incluse, la période s'arrête donc la veille de la suivante. */
    @Test
    void une_semaine_s_arrete_la_veille_de_la_suivante() {
        assertThat(PeriodeCalendrier.HEBDOMADAIRE.dateFin(debut)).isEqualTo(LocalDate.of(2026, 1, 7));
    }

    @Test
    void un_mois_s_arrete_le_dernier_jour_du_mois() {
        assertThat(PeriodeCalendrier.MENSUELLE.dateFin(debut)).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void un_trimestre_s_arrete_le_dernier_jour_du_trimestre() {
        assertThat(PeriodeCalendrier.TRIMESTRIELLE.dateFin(debut)).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    /** Deux périodes consécutives ne se recouvrent plus d'un jour : c'était tout le défaut. */
    @Test
    void deux_periodes_consecutives_ne_partagent_aucun_jour() {
        LocalDate finPremiere = PeriodeCalendrier.HEBDOMADAIRE.dateFin(debut);
        LocalDate debutSeconde = debut.plusWeeks(1);
        assertThat(finPremiere).isBefore(debutSeconde);
        assertThat(finPremiere.plusDays(1)).isEqualTo(debutSeconde);
    }
}
