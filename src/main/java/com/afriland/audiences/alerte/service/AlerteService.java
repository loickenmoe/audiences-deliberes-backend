package com.afriland.audiences.alerte.service;

import com.afriland.audiences.alerte.domain.Alerte;
import com.afriland.audiences.alerte.domain.AlerteDeclenchee;
import com.afriland.audiences.alerte.domain.CanalAlerte;
import com.afriland.audiences.alerte.domain.StatutAlerte;
import com.afriland.audiences.alerte.domain.StatutDiffusion;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.mapper.AlerteMapper;
import com.afriland.audiences.alerte.repository.AlerteRepository;
import com.afriland.audiences.alerte.web.dto.AlerteResponse;
import com.afriland.audiences.alerte.web.dto.StatistiquesDiffusionResponse;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Point d'entrée unique de déclenchement des alertes (RG-ALR-01/02/03), sur le même principe que
 * {@link com.afriland.audiences.common.audit.HistoriqueActionService} : les autres modules
 * métier (M6+) appelleront {@link #declencher} après l'événement concerné, sans que {@code
 * alerte} ait à connaître ces modules en retour (cf. Javadoc {@link
 * com.afriland.audiences.alerte.domain.Alerte}).
 * <p>
 * <b>Canal applicatif (Q-37)</b> : chaque alerte est persistée avec {@code canal = APPLICATIF} et
 * diffusée en temps réel par WebSocket (Q-32) au destinataire s'il est connecté, avec {@code GET
 * /alertes/mes-notifications} comme repli garanti (l'enregistrement en base est inconditionnel).
 * <p>
 * <b>Canaux externes (Q-118)</b> : Outlook et Teams passent par le Notification Engine de la banque.
 * Ce service n'en sait rien : il publie {@link AlerteDeclenchee} dans la transaction de l'action
 * métier, et le module notification met en file les envois que les réglages du DJ prévoient. La
 * table {@code alerte} reste la seule représentation du canal applicatif, comme Q-37 le recommandait.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlerteService {

    private static final String DESTINATION_UTILISATEUR = "/queue/alertes";

    private final AlerteRepository alerteRepository;
    private final AlerteMapper alerteMapper;
    private final CurrentUserService currentUserService;
    private final SimpMessagingTemplate messagingTemplate;
    /** Sessions STOMP vivantes, indexées par le {@code sub} du jeton — cf. {@code pousserNotification}. */
    private final SimpUserRegistry simpUserRegistry;
    /** Prolongements de l'alerte hors du canal applicatif (notifications externes, Q-118). */
    private final ApplicationEventPublisher evenements;

    @Transactional
    public Alerte declencher(TypeAlerte type, Long dossierId, Utilisateur destinataire, String declencheur) {
        Alerte alerte = Alerte.builder()
                .dossierId(dossierId)
                .destinataire(destinataire)
                .type(type)
                .declencheur(declencheur)
                .canal(CanalAlerte.APPLICATIF)
                .statut(StatutAlerte.DECLENCHEE)
                .build();
        alerte = alerteRepository.save(alerte);
        // L'issue de la diffusion est posée sur une entité **déjà gérée**, dans la transaction
        // ouverte par @Transactional : le suivi des modifications la persiste au commit. Un second
        // save() serait redondant, et donnerait à croire que l'enregistrement de l'alerte dépend du
        // succès de sa diffusion — c'est l'inverse, la persistance est inconditionnelle (Q-32).
        pousserNotification(destinataire, alerte);
        evenements.publishEvent(new AlerteDeclenchee(alerte.getId(), type, dossierId, destinataire));
        return alerte;
    }

    /**
     * Diffuse l'alerte en temps réel et <b>enregistre l'issue</b> (Q-109, NF-ALR-02).
     *
     * <p><b>Pourquoi observer la présence du destinataire avant de pousser.</b>
     * {@code convertAndSendToUser} ne lève aucune exception lorsque personne n'est abonné à la file
     * visée : elle réussit silencieusement. Se contenter du {@code try/catch} reviendrait donc à
     * déclarer 100 % de diffusions réussies en permanence, panne comprise. C'est
     * {@link SimpUserRegistry} qui distingue les deux cas — il connaît les sessions STOMP vivantes,
     * indexées par le nom du principal, c'est-à-dire le {@code sub} du jeton Keycloak, celui-là
     * même que porte {@code Utilisateur.keycloakId}.
     *
     * <p><b>Le repli reste garanti</b> (Q-32) : l'alerte est persistée avant toute tentative de
     * diffusion, et {@code GET /alertes/mes-notifications} la sert quoi qu'il arrive. Une issue
     * {@link StatutDiffusion#DIFFEREE} n'est pas une perte, seulement une remise plus tardive.
     */
    private void pousserNotification(Utilisateur destinataire, Alerte alerte) {
        boolean connecte = simpUserRegistry.getUser(destinataire.getKeycloakId()) != null;
        alerte.setDateDiffusion(LocalDateTime.now());
        try {
            messagingTemplate.convertAndSendToUser(destinataire.getKeycloakId(), DESTINATION_UTILISATEUR,
                    alerteMapper.toResponse(alerte));
            alerte.setStatutDiffusion(connecte ? StatutDiffusion.DIFFUSEE : StatutDiffusion.DIFFEREE);
        } catch (MessagingException e) {
            // Le courtier est indisponible ou le transport est rompu : c'est le seul cas où le canal
            // applicatif n'a pas fait son travail, et le seul que NF-ALR-02 compte contre nous.
            // L'alerte reste consultable via GET /alertes/mes-notifications — rien n'est perdu, mais
            // la remise n'est plus immédiate et cela doit se voir dans le taux.
            alerte.setStatutDiffusion(StatutDiffusion.ECHEC);
            log.warn("Échec de la diffusion WebSocket de l'alerte {} au destinataire {} : {}",
                    alerte.getId(), destinataire.getId(), e.getMessage());
        }
    }

    /**
     * Taux de délivrance du canal applicatif sur une fenêtre (Q-109, NF-ALR-02).
     *
     * <p><b>Deux taux, pas un.</b> Les confondre serait la première erreur. Le taux de
     * <i>délivrance</i> mesure ce que NF-ALR-02 exige — le canal a-t-il fait son travail —
     * et ne retient donc que les échecs contre nous. Le taux de <i>diffusion temps réel</i>
     * mesure tout autre chose : à quel point les destinataires sont connectés quand on les alerte.
     * Un second taux bas n'est pas un défaut du système ; un premier taux bas, si.
     *
     * <p>Les alertes antérieures à V16, dont l'issue n'a pas été observée, sont exclues : le
     * dénominateur ne compte que ce qui a été réellement mesuré.
     */
    @Transactional(readOnly = true)
    public StatistiquesDiffusionResponse statistiquesDiffusion(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut.isAfter(dateFin)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début ne peut pas être postérieure à la date de fin");
        }
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.plusDays(1).atStartOfDay();

        long diffusees = alerteRepository.compterParDiffusion(StatutDiffusion.DIFFUSEE, debut, fin);
        long differees = alerteRepository.compterParDiffusion(StatutDiffusion.DIFFEREE, debut, fin);
        long echecs = alerteRepository.compterParDiffusion(StatutDiffusion.ECHEC, debut, fin);
        long nonTraitees = alerteRepository.compterNonTraitees(debut, fin);

        return StatistiquesDiffusionResponse.calculer(dateDebut, dateFin, diffusees, differees, echecs, nonTraitees);
    }

    @Transactional(readOnly = true)
    public PageResponse<AlerteResponse> mesNotifications(StatutAlerte statut, TypeAlerte type,
                                                            Long dossierId, Pageable pageable) {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        var page = alerteRepository.rechercher(utilisateur.getId(), statut, type, dossierId, pageable);
        return PageResponse.of(page, alerteMapper::toResponse);
    }

    /**
     * Anti-doublon pour un déclenchement planifié (ex. {@code CHARGE_MAX_AVOCAT}) : indique si
     * une alerte du même type, pour le même destinataire, portant le même marqueur, est déjà
     * {@code DECLENCHEE} — inutile de la redéclencher tant qu'elle n'a pas été traitée.
     */
    @Transactional(readOnly = true)
    public boolean estDejaSignalee(TypeAlerte type, Long destinataireId, String marqueur) {
        return alerteRepository.existsByTypeAndDestinataireIdAndStatutAndDeclencheurStartingWith(
                type, destinataireId, StatutAlerte.DECLENCHEE, marqueur);
    }

    /**
     * Anti-doublon pour un événement à signaler au plus une fois dans son existence (ex. rappel
     * J-N d'une audience, échéance d'une alarme) — contrairement à {@link #estDejaSignalee},
     * un traitement ultérieur de l'alerte n'autorise pas un nouveau déclenchement du même
     * événement : le marqueur doit encoder l'identité de l'événement lui-même (ex.
     * {@code "[audienceId:5;J-7] "}), pas seulement celle du sujet.
     */
    @Transactional(readOnly = true)
    public boolean aDejaEteDeclenchee(TypeAlerte type, Long destinataireId, String marqueur) {
        return alerteRepository.existsByTypeAndDestinataireIdAndDeclencheurStartingWith(type, destinataireId, marqueur);
    }

    @Transactional
    public AlerteResponse traiter(Long alerteId) {
        Alerte alerte = alerteRepository.findById(alerteId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Alerte introuvable : " + alerteId));
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        if (!alerte.getDestinataire().getId().equals(utilisateur.getId())) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Seul le destinataire de l'alerte peut la marquer comme traitée");
        }
        alerte.setStatut(StatutAlerte.TRAITEE);
        alerte.setDateTraitement(LocalDateTime.now());
        alerte = alerteRepository.save(alerte);
        return alerteMapper.toResponse(alerte);
    }
}
