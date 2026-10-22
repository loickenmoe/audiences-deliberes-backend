package com.afriland.audiences.dossier.web.dto;

import com.afriland.audiences.dossier.domain.TypeEtape;
import jakarta.validation.constraints.NotNull;

/**
 * Direct creation of a step (UC-DOS-04 alt. 3-a/3-b: unknown predecessors). Missing intermediate
 * steps are created automatically in OUVERTURE.
 * <p>
 * Not part of the API contract (which only updates the status of an existing step); needed to
 * create a later step directly (RG-DOS-04).
 */
public record CreerEtapeRequest(@NotNull TypeEtape type) {
}
