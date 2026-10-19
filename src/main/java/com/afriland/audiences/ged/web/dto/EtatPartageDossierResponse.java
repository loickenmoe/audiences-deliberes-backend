package com.afriland.audiences.ged.web.dto;

/**
 * What sharing a document will produce for the lawyer on this case file.
 *
 * <p>{@code avocatConstitue} is true if at least one lawyer <b>assigned</b> to the case file is
 * <b>appointed</b> on it, the same rule that opens documents to them. If false, a shared document
 * waits for a letter to be signed: the person sharing must know, otherwise they see "Shared" while
 * the lawyer sees nothing.
 */
public record EtatPartageDossierResponse(boolean avocatConstitue) {
}
