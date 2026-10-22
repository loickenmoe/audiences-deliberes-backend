package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.Size;

/** Note on a hearing addressed to the assigned lawyer. Empty or null clears it. */
public record NoteAvocatRequest(@Size(max = 4000) String note) {
}
