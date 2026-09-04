package com.afriland.audiences.dossier.mapper;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.Parties;
import com.afriland.audiences.dossier.web.dto.DossierResponse;
import com.afriland.audiences.dossier.web.dto.EtapeProcedureResponse;
import com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse;
import com.afriland.audiences.dossier.web.dto.PartiesDto;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Assemble {@link DossierResponse} à partir d'un {@link Dossier} et des collections associées
 * (étapes, affectations, historique), chargées séparément par le service. Composant simple plutôt
 * qu'un {@code @Mapper} MapStruct : la composition depuis plusieurs sources (pas un simple mapping
 * champ-à-champ) se prête mal à la génération automatique — cf. {@link EtapeProcedureMapper},
 * {@link AuditSeuilMapper}, {@link HistoriqueActionMapper} pour les cas où MapStruct s'applique.
 */
@Component
public class DossierMapper {

    public PartiesDto toPartiesDto(Parties parties) {
        if (parties == null) {
            return null;
        }
        return new PartiesDto(parties.getDemandeur(), parties.getDefendeur());
    }

    public Parties toParties(PartiesDto dto) {
        if (dto == null) {
            return null;
        }
        return new Parties(dto.demandeur(), dto.defendeur());
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
                etapes,
                juristesAffectes,
                avocatsAffectes,
                historique,
                List.of(),
                dossier.getCreatedAt(),
                dossier.getUpdatedAt());
    }
}
