package com.afriland.audiences.dossier.mapper;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.Parties;
import com.afriland.audiences.dossier.web.dto.DossierResponse;
import com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse;
import com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse;
import com.afriland.audiences.dossier.web.dto.PartiesDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Builds {@link DossierResponse} from a {@link Dossier} and its related collections (steps,
 * assignments, history), loaded separately by the service. A plain component rather than a
 * MapStruct {@code @Mapper} because it composes several sources; see {@link EtapeProcedureMapper},
 * {@link AuditSeuilMapper}, {@link HistoriqueActionMapper} for MapStruct cases.
 */
@Component
public class DossierMapper {

    public PartiesDto toPartiesDto(Parties parties) {
        if (parties == null) {
            return null;
        }
        return new PartiesDto(nettoyer(parties.getDemandeurs()), nettoyer(parties.getDefendeurs()));
    }

    public Parties toParties(PartiesDto dto) {
        if (dto == null) {
            return null;
        }
        return new Parties(nettoyer(dto.demandeurs()), nettoyer(dto.defendeurs()));
    }

    /**
     * Trimmed names, blanks removed, never null. Cleaning applies both ways so that existing cases
     * are also read back clean.
     */
    private static List<String> nettoyer(List<String> valeurs) {
        if (valeurs == null) {
            return List.of();
        }
        return valeurs.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(valeur -> !valeur.isEmpty())
                .toList();
    }

    public DossierResponse toResponse(Dossier dossier, List<EtapeProcedureResponse> etapes,
                                       List<Long> juristesAffectes, List<Long> avocatsAffectes,
                                       List<HistoriqueActionResponse> historique) {
        return new DossierResponse(
                dossier.getId(),
                dossier.getReference(),
                dossier.getNature(),
                dossier.getCategorie(),
                dossier.getJuridictionSaisie(),
                dossier.getClient() != null ? dossier.getClient().getId() : null,
                dossier.getClient() != null ? dossier.getClient().getNom() : null,
                toPartiesDto(dossier.getParties()),
                dossier.getRisqueEncouru(),
                dossier.getRecoursExerces(),
                dossier.getMotifRenvoi(),
                dossier.getCodeAgenceOrigine(),
                dossier.getCodeAgenceContentieuse(),
                dossier.getDossierCreditOrigine(),
                dossier.getPvTransfert(),
                dossier.getIncidentCompteReference(),
                dossier.getElementsJustificatifs(),
                dossier.getChampAlarme(),
                dossier.getEstSensible(),
                dossier.getSeuilMontant(),
                dossier.getTypeClientSensible(),
                dossier.getSeuilOrigine(),
                dossier.getSensibiliteStatut(),
                dossier.getSensibiliteMotifRejet(),
                dossier.getSensibiliteMotifRetrait(),
                etapes,
                juristesAffectes,
                avocatsAffectes,
                historique,
                List.of(),
                dossier.getCreatedAt(),
                dossier.getUpdatedAt());
    }
}
