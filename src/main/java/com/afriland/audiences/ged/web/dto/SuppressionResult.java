package com.afriland.audiences.ged.web.dto;

/**
 * Enveloppe interne du double comportement de {@code DELETE /ged/documents/{id}} (RG-GED-05) :
 * {@code immediate=true} (DJ/DJA, 200, {@code document} renseigné) ou {@code immediate=false}
 * (juriste auteur, 202, {@code demande} renseignée) — jamais les deux à la fois.
 */
public record SuppressionResult(boolean immediate, DocumentResponse document, DemandeSuppressionResponse demande) {
}
