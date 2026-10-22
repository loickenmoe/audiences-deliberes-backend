package com.afriland.audiences.portail.web.dto;

/**
 * A case file as seen by the lawyer in charge, deliberately limited to identification. The lawyer is
 * an external provider who only needs to recognise the file when filing a fee request (US 4.1) or a
 * report (US 5.1). Client, risk, sensitivity and assigned legal officers are withheld so the bank's
 * portfolio cannot be extracted by a third party.
 */
public record DossierAvocatResponse(
        Long id,
        String reference,
        String nature,
        String juridictionSaisie) {
}
