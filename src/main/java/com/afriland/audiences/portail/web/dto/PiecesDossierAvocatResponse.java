package com.afriland.audiences.portail.web.dto;

import java.util.List;

/**
 * The shared documents of a case file, and why there are none. {@code constitue} is separate from
 * the list because "no document" and "not yet engaged" are distinct for the lawyer: the latter is
 * resolved by signing a letter, the former by asking the legal officer.
 */
public record PiecesDossierAvocatResponse(boolean constitue, List<PieceAvocatResponse> pieces) {
}
