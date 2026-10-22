package com.afriland.audiences.publication.service;

import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.domain.StatutAudience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.dossier.domain.DossierAvocat;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.repository.CompteRenduAudienceRepository;
import com.afriland.audiences.publication.repository.DemandeExplicationRepository;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RG-INT-11: reminds the lawyer whose hearing report is missing.
 *
 * <p>The lawyer is notified <i>before</i> the hearing (D-7, D-3, D-1, RG-AUD-03); this covers the
 * period after it, when a missing report otherwise produced no signal.
 *
 * <p><b>Why it lives in {@code publication}</b>: it needs hearings, reports and assigned lawyers.
 * {@code publication} already depends on all three; {@code audience} does not know publications,
 * and placing it there would create a cycle.
 *
 * <p><b>Repeated reminders</b>: first after {@code DELAI_RAPPEL_CR_AVOCAT} days, then every
 * {@code DELAI_RAPPEL_CR_AVOCAT} days until the report is filed, which stops them naturally.
 *
 * <p><b>No stored state</b>: the reminder rank is derived from elapsed time and included in the
 * event reference; the notification engine's deduplication ensures two runs on the same day send
 * only one message and a restart replays nothing (same approach as {@code RappelAudienceScheduler}).
 * Trade-off: a day when the job does not run is a lost reminder, not caught up the next day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompteRenduAttenduScheduler {

    private static final String CLE_DELAI = "DELAI_RAPPEL_CR_AVOCAT";

    /**
     * Profiles notified of repeated failures: the Legal Director and the Deputy.
     *
     * <p>Read as profiles, <b>never through a role</b>: {@code ROLE_JURISTE} is composite and would
     * cover both, but also every legal officer.
     */
    private static final List<String> PROFILS_AVERTIS = List.of("DJ", "DJA");

    /** Two consecutive hearings without a report: the business threshold. */
    private static final int MANQUEMENTS_AVANT_ALERTE = 2;

    /**
     * Number of reminders after which they stop.
     *
     * <p>Without a limit, a lawyer would be reminded every two days for years about an old hearing.
     * After five unanswered reminders, the problem is no longer an oversight.
     *
     * <p>A constant rather than a parameter: this number is an assumption, to be replaced by a setting
     * if the Legal Department wants to choose it.
     */
    private static final long RELANCES_MAX = 5;

    /** A rejected report does not count: the hearing still has no report. */
    private static final Set<StatutPublication> DEPOSE_OU_VALIDE =
            Set.of(StatutPublication.DEPOSE, StatutPublication.VALIDE);

    private final AudienceRepository audienceRepository;
    private final CompteRenduAudienceRepository compteRenduAudienceRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final NotificationPrestataireService notificationPrestataireService;
    private final ConfigurationService configurationService;
    private final UtilisateurRepository utilisateurRepository;
    private final AlerteService alerteService;
    private final DemandeExplicationRepository demandeExplicationRepository;

    @Scheduled(cron = "${publication.relance-compte-rendu.cron:0 30 6 * * *}")
    @Transactional
    public void verifier() {
        int delai = delaiEnJours();
        if (delai <= 0) {
            return;
        }
        LocalDate aujourdHui = LocalDate.now();
        /*
         * Lawyers to whom management has written and who have not answered yet.
         *
         * A manager writing in person takes precedence over automatic reminders: sending one every two
         * days while a request for explanation awaits an answer would be noise and weaken both. Reminders
         * resume once the lawyer answers, since the report is still missing.
         *
         * Queried once per run, not per hearing: the answer does not change from one row to the next.
         */
        Set<Long> dispenses = Set.copyOf(demandeExplicationRepository.avocatsEnAttenteDeReponse());

        for (Audience audience : audienceRepository.findByDatePlanifieeBeforeAndStatutNot(
                aujourdHui, StatutAudience.ANNULEE)) {
            long jours = ChronoUnit.DAYS.between(audience.getDatePlanifiee(), aujourdHui);
            if (estARelancer(audience, jours, delai)) {
                relancer(audience, jours / delai, jours, dispenses);
            }
        }
    }

    /** Checks run in this order, cheapest first: the database is queried last, only when needed. */
    private boolean estARelancer(Audience audience, long jours, int delai) {
        // First reminder at the end of the delay, then at each multiple: in between, there is
        // nothing to do.
        if (jours < delai || jours % delai != 0) {
            return false;
        }
        if (jours / delai > RELANCES_MAX) {
            return false;
        }
        // A closed or archived step means the procedure moved on without this report; reminding
        // the lawyer about a finished case file would only confuse them.
        if (audience.getEtape() != null && audience.getEtape().getStatutCycleVie() != null
                && audience.getEtape().getStatutCycleVie().estFerme()) {
            return false;
        }
        return !compteRenduAudienceRepository.existsByAudienceIdAndStatutValidationIn(
                audience.getId(), DEPOSE_OU_VALIDE);
    }

    private void relancer(Audience audience, long rang, long joursEcoules, Set<Long> dispenses) {
        Long dossierId = audience.getDossier().getId();
        List<DossierAvocat> affectations = dossierAvocatRepository.findByDossierId(dossierId);
        if (affectations.isEmpty()) {
            // A case file with no assigned lawyer has no one to remind. Not a scheduler error, but a
            // report nobody owes: it is logged once.
            log.info("Compte rendu manquant sur l'audience {} ({} jours), aucun avocat affecté au "
                    + "dossier {}", audience.getId(), joursEcoules, dossierId);
            return;
        }
        for (DossierAvocat affectation : affectations) {
            // A request for explanation is awaiting an answer: no automatic reminder for this lawyer.
            if (dispenses.contains(affectation.getAvocat().getId())) {
                continue;
            }
            notificationPrestataireService.notifier(
                    EvenementPrestataire.COMPTE_RENDU_ATTENDU,
                    affectation.getAvocat(),
                    dossierId,
                    "cr-attendu-" + audience.getId() + "-relance-" + rang);
        }
    }


    /**
     * Two consecutive hearings without a report are escalated to the Legal Director.
     *
     * <p>Unlike reminders, which reason <b>per hearing</b>, this method reasons <b>per lawyer</b>
     * across all their case files: it is the person's failures that count.
     *
     * <p><b>Only the current streak is considered</b>: the lawyer's due hearings are sorted by date and
     * walked back from the end while the report is missing. A lawyer who has resumed reporting is no
     * longer flagged, and the first run after go-live does not raise an alert for every old streak.
     *
     * <p><b>One alert per streak, not per day</b>: the marker holds the streak's first hearing, so a
     * third missed hearing extends the same streak and notifies no one.
     *
     * <p>The application only reports; <b>management decides</b> whether to send a request for
     * explanation. The alert goes to the Legal Director <b>and the Deputy</b>, so that one absence
     * does not delay it.
     */
    @Scheduled(cron = "${publication.manquement-compte-rendu.cron:0 45 6 * * *}")
    @Transactional
    public void signalerManquementsRepetes() {
        int delai = delaiEnJours();
        if (delai <= 0) {
            return;
        }
        List<Utilisateur> directeurs = PROFILS_AVERTIS.stream()
                .map(utilisateurRepository::findByProfilCodeAndActifTrue)
                .flatMap(List::stream)
                .toList();
        if (directeurs.isEmpty()) {
            // No one to notify: not a scheduler error, but worth logging.
            log.info("Manquements de comptes rendus non signales : ni Directeur Juridique ni adjointe actifs");
            return;
        }

        for (Map.Entry<Avocat, List<Audience>> entree : audiencesEchuesParAvocat(delai).entrySet()) {
            List<Audience> serie = serieDeManquementsEnCours(entree.getValue());
            if (serie.size() < MANQUEMENTS_AVANT_ALERTE) {
                continue;
            }
            signaler(entree.getKey(), serie, directeurs);
        }
    }

    /**
     * Hearings whose filing delay has elapsed, sorted by lawyer and date.
     *
     * <p>"Due" has the <b>same meaning</b> as for reminders: delay elapsed, hearing not cancelled, step
     * still open. Any other threshold would flag a hearing for which the lawyer was never reminded.
     */
    private Map<Avocat, List<Audience>> audiencesEchuesParAvocat(int delai) {
        LocalDate limite = LocalDate.now().minusDays(delai);
        List<Audience> echues = audienceRepository
                .findByDatePlanifieeBeforeAndStatutNot(LocalDate.now(), StatutAudience.ANNULEE)
                .stream()
                .filter(audience -> !audience.getDatePlanifiee().isAfter(limite))
                .filter(audience -> audience.getEtape() == null
                        || audience.getEtape().getStatutCycleVie() == null
                        || !audience.getEtape().getStatutCycleVie().estFerme())
                .sorted(Comparator.comparing(Audience::getDatePlanifiee).thenComparing(Audience::getId))
                .toList();

        Map<Avocat, List<Audience>> parAvocat = new LinkedHashMap<>();
        for (Audience audience : echues) {
            for (DossierAvocat affectation : dossierAvocatRepository
                    .findByDossierId(audience.getDossier().getId())) {
                parAvocat.computeIfAbsent(affectation.getAvocat(), avocat -> new ArrayList<>())
                        .add(audience);
            }
        }
        return parAvocat;
    }

    /** The streak of failures ending the list: empty as soon as the latest report exists. */
    private List<Audience> serieDeManquementsEnCours(List<Audience> audiences) {
        List<Audience> serie = new ArrayList<>();
        for (int rang = audiences.size() - 1; rang >= 0; rang--) {
            Audience audience = audiences.get(rang);
            if (compteRenduAudienceRepository.existsByAudienceIdAndStatutValidationIn(
                    audience.getId(), DEPOSE_OU_VALIDE)) {
                break;
            }
            serie.add(0, audience);
        }
        return serie;
    }

    private void signaler(Avocat avocat, List<Audience> serie, List<Utilisateur> directeurs) {
        // The marker holds the **first** hearing of the streak: a growing streak stays the same one
        // and alerts only once.
        String marqueur = "[avocat:" + avocat.getId() + ";serie:" + serie.get(0).getId() + "] ";
        Audience derniere = serie.get(serie.size() - 1);
        String declencheur = marqueur + "L'avocat " + avocat.getNom() + " n'a pas depose le compte "
                + "rendu de " + serie.size() + " audiences consecutives, la derniere du "
                + derniere.getDatePlanifiee() + " sur le dossier "
                + derniere.getDossier().getReference() + " (observation 8).";

        for (Utilisateur directeur : directeurs) {
            if (alerteService.aDejaEteDeclenchee(TypeAlerte.MANQUEMENT_COMPTE_RENDU,
                    directeur.getId(), marqueur)) {
                continue;
            }
            alerteService.declencher(TypeAlerte.MANQUEMENT_COMPTE_RENDU,
                    derniere.getDossier().getId(), directeur, declencheur);
        }
    }

    /**
     * The delay, or 0 if the parameter cannot be read.
     *
     * <p>A scheduler must not fail on a dubious configuration: it stays silent, logs it, and the other
     * nightly jobs continue.
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
