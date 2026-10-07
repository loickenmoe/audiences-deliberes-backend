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
     * Les profils avertis d'un manquement repete : le Directeur Juridique et son adjointe.
     *
     * <p>Lus tels quels, et <b>jamais par un role</b> : {@code ROLE_JURISTE} est composite (Q-81) et
     * couvrirait les deux, mais aussi tous les juristes. C'est le trou que Q-126 a ferme ailleurs,
     * et il ne se rouvre pas ici.
     */
    private static final List<String> PROFILS_AVERTIS = List.of("DJ", "DJA");

    /** Deux audiences de suite sans compte rendu : c'est le seuil arrete par le metier. */
    private static final int MANQUEMENTS_AVANT_ALERTE = 2;

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
         * Les avocats à qui la direction a écrit et qui n'ont pas encore répondu (N7 temps 2).
         *
         * **Un responsable qui écrit en personne couvre la machine.** Continuer à envoyer un rappel
         * automatique tous les deux jours pendant qu'une demande d'explication attend sa réponse
         * ferait du bruit, et affaiblirait les deux : le rappel, qu'on cesse de lire, et la demande,
         * noyée dedans. La relance reprend à la réponse - le compte rendu manque toujours, et c'est
         * de nouveau l'affaire du rappel.
         *
         * La question est posée **une fois par passage**, et non une fois par audience : la réponse
         * ne change pas d'une ligne à l'autre, et le planificateur en parcourt des centaines.
         */
        Set<Long> dispenses = Set.copyOf(demandeExplicationRepository.avocatsEnAttenteDeReponse());

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
            relancer(audience, rang, jours, dispenses);
        }
    }

    private void relancer(Audience audience, long rang, long joursEcoules, Set<Long> dispenses) {
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
            // Une demande d'explication attend sa réponse : la machine se tait pour celui-ci.
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
     * N7 : deux audiences consecutives sans compte rendu remontent au Directeur Juridique
     * (observation 8, definition arretee le 2026-10-07).
     *
     * <p><b>Ce que compte cette methode, et que l'autre ne comptait pas.</b> La relance raisonne
     * <b>par audience</b> : elle ne sait pas qu'un meme avocat en a manque deux. Celle-ci raisonne
     * <b>par avocat</b>, a travers tous ses dossiers - c'est la lecture que le metier a retenue :
     * ce sont les manquements de la personne qui comptent, pas ceux d'une affaire.
     *
     * <p><b>Seule la serie en cours est regardee.</b> On prend les audiences echues de l'avocat dans
     * l'ordre des dates et on remonte depuis la fin tant que le compte rendu manque. Deux raisons :
     * un avocat qui a manque deux audiences puis s'est remis a rendre compte n'a plus a etre
     * signale - la serie est rompue, et nommer un comportement corrige ferait du bruit ; et au
     * premier passage apres la mise en service, regarder tout l'historique produirait une alerte
     * par serie ancienne, c'est-a-dire une avalanche le matin de la livraison.
     *
     * <p><b>Une alerte par serie, pas une par jour.</b> « Deux audiences sans compte rendu » est un
     * <b>etat</b> : il reste vrai tant que rien n'arrive. Le marqueur porte donc la premiere
     * audience de la serie, et non la date : la troisieme audience manquee allonge la meme serie et
     * ne reveille personne. Le metier a dit « au deuxieme manquement », pas « a chaque manquement ».
     *
     * <p>L'application constate ; <b>c'est la direction qui decide</b> d'envoyer une demande
     * d'explication. L'alerte va au Directeur Juridique <b>et a son adjointe</b> : l'un des deux
     * peut etre absent le jour ou un avocat decroche, et un signalement qui attend un retour de
     * conge n'en est plus un.
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
            // Personne a prevenir : ce n'est pas une anomalie du planificateur, mais cela se dit.
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
     * Les audiences dont le delai de depot est ecoule, rangees par avocat et par date.
     *
     * <p>« Echue » a ici le <b>meme sens</b> que pour la relance : le delai passe, l'audience non
     * annulee, l'etape encore ouverte. Un autre seuil ferait signaler au DJ une audience pour
     * laquelle l'avocat n'a jamais ete relance.
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

    /** La suite de manquements qui termine la liste : vide des que le dernier compte rendu existe. */
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
        // Le marqueur porte la **premiere** audience de la serie : une serie qui s'allonge reste
        // la meme, et n'alerte qu'une fois.
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
