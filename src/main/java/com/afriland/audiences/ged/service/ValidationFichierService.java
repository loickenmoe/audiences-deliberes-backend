package com.afriland.audiences.ged.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.ged.domain.FormatDocument;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Contrôle de format et de poids d'un fichier reçu (RG-GED-04/08).
 *
 * <p>Extrait de {@code GedService} en M19 (Q-97) : les pièces justificatives d'une demande de frais
 * atterrissent dans le même stockage que les documents GED et doivent donc obéir aux mêmes règles.
 * Les dupliquer aurait fait diverger deux contrôles que le DJ pilote par une **seule** paire de
 * paramètres, {@code FORMATS_GED} et {@code TAILLE_MAX_GED}.
 */
@Service
@RequiredArgsConstructor
public class ValidationFichierService {

    private final ConfigurationService configurationService;

    /**
     * @return le format reconnu, dérivé de l'extension
     * @throws ApiException 400 {@code ERR-008} si l'extension est absente, inconnue, désactivée par
     *                      la configuration, ou si le fichier dépasse le plafond
     */
    public FormatDocument valider(MultipartFile fichier) {
        String nom = fichier.getOriginalFilename();
        int pointIdx = nom == null ? -1 : nom.lastIndexOf('.');
        if (!StringUtils.hasText(nom) || pointIdx < 0 || pointIdx == nom.length() - 1) {
            throw ApiException.badRequest(ErrorCodes.DOCUMENT_NON_CONFORME,
                    "Le fichier doit avoir une extension identifiable");
        }
        String extension = nom.substring(pointIdx + 1).toUpperCase();
        FormatDocument format;
        try {
            format = FormatDocument.valueOf(extension);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(ErrorCodes.DOCUMENT_NON_CONFORME,
                    "Format de fichier non conforme : " + extension);
        }

        Set<String> formatsAutorises = Set.of(configurationService.getOrThrow("FORMATS_GED").getValeur().split(","));
        if (!formatsAutorises.contains(format.name())) {
            throw ApiException.badRequest(ErrorCodes.DOCUMENT_NON_CONFORME,
                    "Format « " + format + " » désactivé par la configuration FORMATS_GED");
        }

        long tailleMax = Long.parseLong(configurationService.getOrThrow("TAILLE_MAX_GED").getValeur());
        if (fichier.getSize() > tailleMax) {
            throw ApiException.badRequest(ErrorCodes.DOCUMENT_NON_CONFORME,
                    "Poids du fichier (" + fichier.getSize() + " o) supérieur au maximum autorisé ("
                            + tailleMax + " o)");
        }
        return format;
    }
}
