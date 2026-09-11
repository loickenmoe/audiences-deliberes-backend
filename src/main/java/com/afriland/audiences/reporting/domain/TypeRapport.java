package com.afriland.audiences.reporting.domain;

/**
 * Granularité d'un rapport périodique (FR-REP-01, US 8.4). {@code dateDebut}/{@code dateFin} sont
 * fournies explicitement par l'appelant (contrairement à {@code PeriodeCalendrier}, Épic 2, qui
 * calcule sa propre {@code dateFin}) — ce type sert d'étiquette de période, sans effet de calcul
 * (Q-63).
 */
public enum TypeRapport {
    JOURNALIER,
    HEBDOMADAIRE,
    MENSUEL,
    TRIMESTRIEL,
    ANNUEL
}
