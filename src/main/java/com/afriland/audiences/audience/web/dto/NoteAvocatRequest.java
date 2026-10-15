package com.afriland.audiences.audience.web.dto;

import jakarta.validation.constraints.Size;

/**
 * La note d'une audience destinée à l'avocat constitué (QF-64). Vide ou nulle, elle est effacée :
 * c'est ainsi que le juriste retire une note devenue sans objet.
 */
public record NoteAvocatRequest(@Size(max = 4000) String note) {
}
