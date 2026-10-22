package com.afriland.audiences.common.reference;

/**
 * Object families whose reference is assigned by the system. The prefix lives here so a reference
 * cannot be built elsewhere with another prefix.
 *
 * <p>Internal references only: a lawyer's invoice reference or an account incident reference are
 * external identifiers, used precisely to reconcile with the external system.
 */
public enum FamilleReference {

    /** Litigation cases: {@code DOS-2026-0001}. */
    DOSSIER("DOS"),

    /** Clients: {@code CLI-2026-0001}. */
    CLIENT("CLI");

    private final String prefixe;

    FamilleReference(String prefixe) {
        this.prefixe = prefixe;
    }

    public String prefixe() {
        return prefixe;
    }
}
