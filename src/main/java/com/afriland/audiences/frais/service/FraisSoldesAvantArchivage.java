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
 * On ne referme pas un dossier en laissant une facture ouverte (métier, 2026-09-29).
 *
 * <p><b>Soldée</b> veut dire <b>payée ou rejetée</b> : ce sont les deux fins du circuit
 * (RG-INT-03/04). Une demande encore déposée, en contrôle de conformité, en opportunité, ou validée
 * mais non payée, est une dette de la banque envers son avocat.
 *
 * <p><b>La condition ne vaut que pour la dernière étape.</b> C'est l'ajustement du 2026-09-29, après
 * cette remarque du métier : un dossier porte <b>plusieurs avocats</b>, donc un flux continu de
 * demandes de frais. Appliquée à chaque étape, la règle aurait rendu la première instance
 * inarchivable pendant des années à cause d'une facture d'appel — et l'aurait rendue inarchivable
 * par quelqu'un qui n'y peut rien : archiver est réservé au juriste ({@code ROLE_JURISTE}), payer à
 * l'assistante ({@code ROLE_ASSISTANTE}).
 *
 * <p>Ce que le métier veut éviter, c'est de <b>ranger un dossier</b> en laissant une facture
 * ouverte ; pas d'empêcher de classer une instance terminée pendant que l'appel se poursuit. La
 * condition ne se déclenche donc qu'au moment où l'on archive ce qui reste : le dernier qui ferme
 * éteint la lumière.
 *
 * <p><b>Ce n'est pas une approximation en attente d'être corrigée.</b> {@code DemandeFrais} est
 * rattachée au dossier et non à une étape, et l'ajout de ce lien a été envisagé puis écarté le
 * 2026-09-29 : il n'attraperait <b>aucune dette</b> que cette condition laisse passer. Une facture
 * impayée à n'importe quelle étape est de toute façon vue ici, au moment où le dossier cesse de
 * vivre. Un contrôle par étape ajouterait des blocages intermédiaires sans ajouter de garantie.
 *
 * <p>Le concept qui manquait n'était pas « l'étape d'une demande de frais », mais « le dossier est
 * clos » — que cette condition reconstitue : la dernière étape archivée, c'est le dossier fermé.
 */
@Component
@RequiredArgsConstructor
public class FraisSoldesAvantArchivage implements ConditionTransitionEtape {

    /** Les deux fins du circuit : au-delà, plus rien n'est dû ni attendu. */
    private static final List<StatutCircuitFrais> SOLDEES =
            List.of(StatutCircuitFrais.PAYEE, StatutCircuitFrais.REJETEE);

    /** Au-delà, la liste des demandes citées cesse d'aider et devient un mur de texte. */
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
     * Cette étape est-elle la dernière que le dossier n'ait pas archivée ?
     *
     * <p>Si d'autres étapes restent ouvertes, le dossier continue de vivre : les frais en cours y
     * appartiennent vraisemblablement, et rien ne justifie d'en bloquer le classement.
     */
    private boolean estLaDerniereEtapeOuverte(Long dossierId, Long etapeId) {
        return etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossierId).stream()
                .filter(autre -> !autre.getId().equals(etapeId))
                .allMatch(autre -> autre.getStatutCycleVie() == StatutCycleVie.ARCHIVE);
    }

    /**
     * Le refus nomme les demandes en cause.
     *
     * <p>Un décompte seul — « 3 demandes » — laisse chercher lesquelles, et auprès de quel avocat.
     * Or celui qui archive n'a pas la main sur le circuit des frais : il doit pouvoir aller trouver
     * la bonne personne, pas partir en enquête.
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

    /** La référence de facture si elle existe, sinon l'avocat : l'un des deux permet de retrouver. */
    private static String designer(DemandeFrais demande) {
        String avocat = demande.getAvocat() == null ? "avocat inconnu" : demande.getAvocat().getNom();
        return demande.getReferenceFacture() == null || demande.getReferenceFacture().isBlank()
                ? avocat
                : demande.getReferenceFacture() + " – " + avocat;
    }
}
