package com.afriland.audiences.frais.service;

import com.afriland.audiences.dossier.domain.EtapeProcedure;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.EtapeProcedureRepository;
import com.afriland.audiences.dossier.service.ConditionTransitionEtape;
import com.afriland.audiences.frais.domain.DemandeFrais;
import com.afriland.audiences.frais.domain.StatutCircuitFrais;
import com.afriland.audiences.frais.repository.DemandeFraisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A case cannot be archived while an invoice is still open.
 *
 * <p><b>Settled</b> means <b>paid or rejected</b>, the two ends of the workflow (RG-INT-03/04).
 *
 * <p>The condition only applies to the <b>last</b> step: a case has several lawyers and a
 * continuous flow of fee requests, so applying it at every step would block archiving a first
 * instance for years because of an appeal invoice, and block someone who cannot act on it
 * (archiving is {@code ROLE_JURISTE}, paying is {@code ROLE_ASSISTANTE}). {@code DemandeFrais} is
 * linked to the case, not to a step: any unpaid invoice is still caught when the last step is archived.
 */
@Component
@RequiredArgsConstructor
public class FraisSoldesAvantArchivage implements ConditionTransitionEtape {

    /** The two ends of the workflow: beyond them, nothing is owed or pending. */
    private static final List<StatutCircuitFrais> SOLDEES =
            List.of(StatutCircuitFrais.PAYEE, StatutCircuitFrais.REJETEE);

    /** Beyond this, the list of cited requests stops helping and becomes a wall of text. */
    private static final int CITATIONS_MAX = 3;

    private final DemandeFraisRepository demandeFraisRepository;
    private final EtapeProcedureRepository etapeProcedureRepository;

    @Override
    public boolean concerne(StatutCycleVie de, StatutCycleVie vers) {
        return de == StatutCycleVie.CLOTURE && vers == StatutCycleVie.ARCHIVE;
    }

    @Override
    public Optional<String> empechement(EtapeProcedure etape) {
        Long dossierId = etape.getDossier() == null ? null : etape.getDossier().getId();
        if (dossierId == null || !estLaDerniereEtapeOuverte(dossierId, etape.getId())) {
            return Optional.empty();
        }

        List<DemandeFrais> enCours =
                demandeFraisRepository.findByDossierIdAndStatutCircuitNotIn(dossierId, SOLDEES);
        if (enCours.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(message(enCours));
    }

    /**
     * Is this the last step of the case not yet archived?
     *
     * <p>If other steps remain open, the case is still alive and pending fees likely belong to them.
     */
    private boolean estLaDerniereEtapeOuverte(Long dossierId, Long etapeId) {
        return etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossierId).stream()
                .filter(autre -> !autre.getId().equals(etapeId))
                .allMatch(autre -> autre.getStatutCycleVie() == StatutCycleVie.ARCHIVE);
    }

    /**
     * The refusal names the requests concerned: the person archiving does not control the fee
     * workflow and must be able to find the right person.
     */
    private static String message(List<DemandeFrais> enCours) {
        String citees = enCours.stream()
                .limit(CITATIONS_MAX)
                .map(FraisSoldesAvantArchivage::designer)
                .collect(Collectors.joining(", "));
        String reste = enCours.size() > CITATIONS_MAX
                ? " et " + (enCours.size() - CITATIONS_MAX) + " autre(s)"
                : "";

        String tete = enCours.size() == 1
                ? "Une demande de frais de ce dossier n'est ni payée ni rejetée"
                : enCours.size() + " demandes de frais de ce dossier ne sont ni payées ni rejetées";

        return tete + " (" + citees + reste + ") : "
                + (enCours.size() == 1 ? "soldez-la" : "soldez-les")
                + " avant d'archiver la dernière étape du dossier.";
    }

    /** The invoice reference if any, otherwise the lawyer: either one makes the request traceable. */
    private static String designer(DemandeFrais demande) {
        String avocat = demande.getAvocat() == null ? "avocat inconnu" : demande.getAvocat().getNom();
        return demande.getReferenceFacture() == null || demande.getReferenceFacture().isBlank()
                ? avocat
                : demande.getReferenceFacture() + " – " + avocat;
    }
}
