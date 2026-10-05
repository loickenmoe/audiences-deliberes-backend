package com.afriland.audiences.publication.service;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.dossier.domain.DossierAvocat;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.repository.CompteRenduAudienceRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * RG-INT-11 (métier du 2026-10-05) : relance de l'avocat dont le compte rendu d'audience manque.
 *
 * <p><b>Le manque que cela comble.</b> L'avocat est prévenu <i>avant</i> l'audience — J-7, J-3, J-1
 * (RG-AUD-03). Après, rien. Une audience tenue dont le compte rendu n'arrivait jamais ne produisait
 * aucun signal, et c'est précisément le cas que la Direction Juridique a relevé en présentation.
 *
 * <p><b>Pourquoi il vit dans {@code publication}.</b> Il lui faut à la fois les audiences, les
 * comptes rendus et les avocats affectés. Le module {@code publication} dépend déjà des trois ;
 * {@code audience} ne connaît pas les publications, et l'y placer créerait un cycle — c'est la même
 * contrainte que celle qui a fait naître {@code ConditionTransitionEtape}.
 *
 * <p><b>Pourquoi la relance se répète.</b> Un rappel unique qu'on manque n'est jamais repris, or
 * c'est l'oubli durable que le métier veut traiter. La cadence vaut le délai : relance au bout de
 * {@code DELAI_RAPPEL_CR_AVOCAT} jours, puis tous les {@code DELAI_RAPPEL_CR_AVOCAT} jours, jusqu'au
 * dépôt — qui l'arrête de lui-même, puisque la condition cesse d'être vraie.
 *
 * <p><b>Pourquoi aucun état n'est stocké.</b> Le rang de la relance se déduit du temps écoulé, et
 * il entre dans la référence de l'événement. La déduplication du moteur de notification fait le
 * reste : deux exécutions du même jour ne produisent qu'un envoi, et un redémarrage ne rejoue rien.
 * C'est le procédé déjà employé par {@code RappelAudienceScheduler} pour ses trois échéances.
 * Le revers est assumé : une journée où le traitement ne tourne pas est une relance
 * perdue, non rattrapée le lendemain — la suivante arrive au multiple suivant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompteRenduAttenduScheduler {

    private static final String CLE_DELAI = "DELAI_RAPPEL_CR_AVOCAT";

    /**
     * Nombre de relances au-delà duquel on cesse.
     *
     * <p>Une relance qui ne s'arrête jamais n'est plus une relance : sans borne, un avocat serait
     * rappelé tous les deux jours, des années durant, au sujet d'une audience de 2024. Au bout de
     * cinq rappels sans réponse, le problème n'est plus un oubli et ne se traite plus par un
     * message automatique.
     *
     * <p>Constante et non paramètre : le métier n'a pas arbitré ce nombre — c'est une hypothèse,
     * posée pour que la fonction soit utilisable, et à remplacer par un réglage le jour où la
     * Direction Juridique voudra le choisir.
     */
    private static final long RELANCES_MAX = 5;

    /** Un compte rendu rejeté ne vaut pas compte rendu : l'audience reste sans récit. */
    private static final Set<StatutPublication> DEPOSE_OU_VALIDE =
            Set.of(StatutPublication.DEPOSE, StatutPublication.VALIDE);

    private final AudienceRepository audienceRepository;
    private final CompteRenduAudienceRepository compteRenduAudienceRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final NotificationPrestataireService notificationPrestataireService;
    private final ConfigurationService configurationService;

    @Scheduled(cron = "${publication.relance-compte-rendu.cron:0 30 6 * * *}")
    @Transactional
    public void verifier() {
        int delai = delaiEnJours();
        if (delai <= 0) {
            return;
        }
        LocalDate aujourdHui = LocalDate.now();

        for (Audience audience : audienceRepository.findByDatePlanifieeBeforeAndStatutNot(
                aujourdHui, StatutAudience.ANNULEE)) {
            long jours = ChronoUnit.DAYS.between(audience.getDatePlanifiee(), aujourdHui);
            // La première relance tombe au bout du délai, les suivantes à chaque multiple : entre
            // deux, il n'y a rien à faire et la journée se passe sans message.
            if (jours < delai || jours % delai != 0) {
                continue;
            }
            long rang = jours / delai;
            if (rang > RELANCES_MAX) {
                continue;
            }
            // Une étape close ou archivée a tourné la page : la procédure a avancé sans ce récit,
            // et réclamer encore ne servirait qu'à troubler l'avocat sur un dossier terminé.
            if (audience.getEtape() != null && audience.getEtape().getStatutCycleVie() != null
                    && audience.getEtape().getStatutCycleVie().estFerme()) {
                continue;
            }
            if (compteRenduAudienceRepository.existsByAudienceIdAndStatutValidationIn(
                    audience.getId(), DEPOSE_OU_VALIDE)) {
                continue;
            }
            relancer(audience, rang, jours);
        }
    }

    private void relancer(Audience audience, long rang, long joursEcoules) {
        Long dossierId = audience.getDossier().getId();
        List<DossierAvocat> affectations = dossierAvocatRepository.findByDossierId(dossierId);
        if (affectations.isEmpty()) {
            // Un dossier sans avocat affecté n'a personne à relancer. Ce n'est pas une anomalie du
            // planificateur, mais c'est un compte rendu que personne ne doit : on le dit une fois.
            log.info("Compte rendu manquant sur l'audience {} ({} jours), aucun avocat affecté au "
                    + "dossier {}", audience.getId(), joursEcoules, dossierId);
            return;
        }
        for (DossierAvocat affectation : affectations) {
            notificationPrestataireService.notifier(
                    EvenementPrestataire.COMPTE_RENDU_ATTENDU,
                    affectation.getAvocat(),
                    dossierId,
                    "cr-attendu-" + audience.getId() + "-relance-" + rang);
        }
    }

    /**
     * Le délai, ou 0 si le paramètre est illisible.
     *
     * <p>Un planificateur ne doit pas faire tomber son exécution sur une configuration douteuse :
     * il se tait, le dit au journal, et les autres traitements de la nuit continuent.
     */
    private int delaiEnJours() {
        try {
            return Integer.parseInt(configurationService.getOrThrow(CLE_DELAI).getValeur().trim());
        } catch (RuntimeException e) {
            log.error("Relance des comptes rendus suspendue : {} illisible", CLE_DELAI, e);
            return 0;
        }
    }
}
