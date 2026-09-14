package com.afriland.audiences.frais.web.dto;

import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.domain.StatutPaiementFrais;

import java.time.LocalDateTime;
import com.afriland.audiences.portail.web.dto.PieceJustificativeResponse;

import java.util.List;

/**
 * Depuis Q-82 : la demande se lit sans autre appel — dossier et avocat nommés, sensibilité et seuil
 * applicable qui expliquent {@code validationConjointeRequise} (RG-INT-03), et décisions du circuit
 * dans l'ordre chronologique.
 * <p>
 * Depuis Q-97 : {@code piecesTelechargeables} porte les pièces réellement stockées, avec une URL
 * pré-signée (10 minutes) — UC-INT-02 étape 3 veut que l'instructeur « affiche la demande, <b>ses
 * pièces justificatives</b> et le résultat du contrôle de conformité ». Renseigné sur le
 * <b>détail</b> seulement, comme l'URL d'un document GED ; vide sur la liste. {@code
 * piecesJustificatives} reste le tableau brut du dictionnaire des données — il peut contenir des
 * entrées antérieures au téléversement, qui ne désignent aucun fichier.
 */
public record DemandeFraisResponse(
        Long id,
        Long dossierId,
        String referenceDossier,
        Long avocatId,
        String avocatNom,
        Double montant,
        List<String> piecesJustificatives,
        StatutCircuitFrais statutCircuit,
        String motifRejet,
        StatutPaiementFrais statutPaiement,
        String referenceFacture,
        Boolean dossierSensible,
        Double seuilApplicable,
        boolean validationConjointeRequise,
        List<ValidationFraisResponse> validations,
        List<PieceJustificativeResponse> piecesTelechargeables,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
