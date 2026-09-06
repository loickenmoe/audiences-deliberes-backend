package com.afriland.audiences.audience.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Une ligne du calendrier des audiences (FR-AUD-05) : juridiction, étape, juriste(s), avocat(s),
 * unité d'origine, compte client, objet (référence du dossier), risque, prochaine date
 * (= {@code datePlanifiee} de cette audience, la plus proche échéance connue pour ce dossier).
 */
public record CalendrierEntreeResponse(
        Long audienceId,
        Long dossierId,
        String referenceDossier,
        String juridiction,
        String etape,
        List<Long> juristesAffectes,
        List<Long> avocatsAffectes,
        String codeAgenceOrigine,
        String clientNom,
        Double risqueEncouru,
        LocalDate datePlanifiee) {
}
