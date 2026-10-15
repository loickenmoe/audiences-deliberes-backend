package com.afriland.audiences.ged.domain;

/**
 * L'état du partage d'une pièce avec l'avocat du dossier (QF-64).
 *
 * <p>Une pièce n'est partagée avec <b>personne en particulier</b> : elle l'est avec les avocats
 * affectés au dossier <b>et constitués</b>, ce qui se vérifie à chaque lecture. Un changement
 * d'avocat retire donc l'accès sur-le-champ, sans rien à défaire ici.
 */
public enum StatutPartageAvocat {
    /** Réservée aux profils internes : l'état de toute pièce tant que personne n'a décidé autrement. */
    NON,
    /** Dossier sensible : un juriste a proposé le partage, le DJ ou la DJA ne l'a pas encore validé. */
    PROPOSE,
    /** Visible des avocats affectés et constitués. */
    PARTAGE
}
