package com.afriland.audiences.ged.web.dto;

/**
 * Internal wrapper for the dual behaviour of {@code DELETE /ged/documents/{id}} (RG-GED-05):
 * {@code immediate=true} (DJ/DJA, 200, {@code document} set) or {@code immediate=false}
 * (uploading legal officer, 202, {@code demande} set), never both.
 */
public record SuppressionResult(boolean immediate, DocumentResponse document, DemandeSuppressionResponse demande) {
}
