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
 * Format and size check of an uploaded file (RG-GED-04/08).
 *
 * <p>Shared by GED documents and expense supporting documents, which land in the same storage and
 * must obey the same rules, both governed by the single pair of parameters {@code FORMATS_GED} and
 * {@code TAILLE_MAX_GED}.
 */
@Service
@RequiredArgsConstructor
public class ValidationFichierService {

    private final ConfigurationService configurationService;

    /**
     * @return the recognised format, derived from the extension
     * @throws ApiException 400 {@code ERR-008} if the extension is missing, unknown, disabled by
     *                      configuration, or if the file exceeds the size limit
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
