package com.afriland.audiences.ged.domain;

/**
 * Sharing state of a document with the case file's lawyer.
 *
 * <p>A document is not shared with <b>a specific person</b>: it is shared with the lawyers
 * assigned to the case file <b>and formally appointed</b>, which is checked on every read.
 * Changing lawyers therefore revokes access immediately, with nothing to undo here.
 */
public enum StatutPartageAvocat {
    /** Internal profiles only: the state of every document until someone decides otherwise. */
    NON,
    /** Sensitive case file: a legal officer proposed sharing, not yet approved by the DJ or DJA. */
    PROPOSE,
    /** Visible to assigned and appointed lawyers. */
    PARTAGE
}
