package com.afriland.audiences.publication.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.publication.domain.DemandeExplication;
import com.afriland.audiences.publication.repository.DemandeExplicationRepository;
import com.afriland.audiences.publication.web.dto.DemandeExplicationResponse;
import com.afriland.audiences.publication.web.dto.DemanderExplicationRequest;
import com.afriland.audiences.publication.web.dto.RepondreExplicationRequest;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Request for explanation and the lawyer's answer.
 *
 * <p>The scheduler tells management that a lawyer missed two consecutive hearings and stops there:
 * the application reports, it does not sanction. This service handles what follows: a manager
 * writes, the lawyer answers, and the exchange is kept.
 *
 * <p><b>A pending request suspends automatic reminders</b> for that lawyer: a manager writing in
 * person takes precedence over the automatic reminder. Reminders resume once the lawyer answers,
 * since the report is still missing.
 *
 * <p>The request itself is never reminded, as no delay has been defined for that. An unanswered
 * request stays visible in management's queue, which decides whether to insist.
 */
@Service
@RequiredArgsConstructor
public class DemandeExplicationService {

    private final DemandeExplicationRepository demandeExplicationRepository;
    private final IntervenantRepository intervenantRepository;
    private final DossierRepository dossierRepository;
    private final CurrentUserService currentUserService;
    private final CompteAvocatService compteAvocatService;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final NotificationPrestataireService notificationPrestataireService;

    /**
     * Management asks a lawyer to explain themselves.
     *
     * <p><b>One pending request at a time</b>: a second one would tell the lawyer nothing more and blur
     * the queue (which one suspends reminders, which one to answer?). Once answered, management can
     * ask again.
     */
    @Transactional
    public DemandeExplicationResponse demander(DemanderExplicationRequest requete) {
        Avocat avocat = avocatOuErreur(requete.avocatId());
        if (demandeExplicationRepository.existsByAvocatIdAndDateReponseIsNull(avocat.getId())) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "Une demande d'explication est déjà en attente de réponse pour cet avocat. "
                            + "Attendez sa réponse avant d'en adresser une autre.");
        }
        Utilisateur auteur = currentUserService.getCurrentUser();
        Dossier dossier = requete.dossierId() == null ? null
                : dossierRepository.findById(requete.dossierId())
                        .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                                "Dossier introuvable : " + requete.dossierId()));

        DemandeExplication demande = demandeExplicationRepository.save(DemandeExplication.builder()
                .avocat(avocat)
                .dossier(dossier)
                .auteur(auteur)
                .contenu(requete.contenu())
                .build());

        if (dossier != null) {
            historiqueActionService.enregistrerPour(auteur, dossier.getId(),
                    TypeActionHistorique.EXPLICATION_DEMANDEE,
                    Map.of("avocatId", avocat.getId(), "demandeId", demande.getId()));
        }

        // In the application, so the lawyer sees it on login; and outside it, because the lawyer may
        // precisely no longer log in, which is what they are being reproached for.
        compteAvocatService.utilisateurDe(avocat).ifPresent(compte ->
                alerteService.declencher(TypeAlerte.DEMANDE_EXPLICATION,
                        dossier == null ? null : dossier.getId(), compte,
                        "La Direction Juridique vous demande de vous expliquer sur des comptes rendus "
                                + "d'audience non déposés (demande " + demande.getId() + ")."));
        notificationPrestataireService.notifier(EvenementPrestataire.DEMANDE_EXPLICATION, avocat,
                dossier == null ? null : dossier.getId(), "explication-" + demande.getId());

        return versReponse(demande);
    }

    /**
     * The lawyer answers, only once.
     *
     * <p>A rewritable answer would prove nothing: it is a dated statement that management may read
     * months later. Correcting it requires a new request.
     */
    @Transactional
    public DemandeExplicationResponse repondre(Long id, RepondreExplicationRequest requete) {
        DemandeExplication demande = demandeExplicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Demande d'explication introuvable : " + id));
        Avocat moi = avocatCourant();
        if (!moi.getId().equals(demande.getAvocat().getId())) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Cette demande d'explication ne vous est pas adressée");
        }
        if (!demande.estEnAttente()) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "Vous avez déjà répondu à cette demande. Une correction passe par une nouvelle demande.");
        }

        demande.setReponse(requete.reponse());
        demande.setDateReponse(LocalDateTime.now());
        demande = demandeExplicationRepository.save(demande);

        Utilisateur auteurDeLaReponse = currentUserService.getCurrentUser();
        if (demande.getDossier() != null) {
            historiqueActionService.enregistrerPour(auteurDeLaReponse, demande.getDossier().getId(),
                    TypeActionHistorique.EXPLICATION_REPONDUE,
                    Map.of("demandeId", demande.getId()));
        }

        // The requester must know an answer has arrived, without having to check back.
        alerteService.declencher(TypeAlerte.DEMANDE_EXPLICATION,
                demande.getDossier() == null ? null : demande.getDossier().getId(),
                demande.getAuteur(),
                "L'avocat " + demande.getAvocat().getNom() + " a répondu à votre demande d'explication "
                        + demande.getId() + ".");

        return versReponse(demande);
    }

    /**
     * Management's queue, or the lawyer's.
     *
     * <p>The scope is decided <b>here</b>, from the caller's identity, not from a UI parameter:
     * otherwise a lawyer could request the whole list.
     */
    @Transactional(readOnly = true)
    public PageResponse<DemandeExplicationResponse> lister(boolean enAttenteSeulement, Pageable pageable) {
        Optional<Avocat> avocat = compteAvocatService.avocatDe(currentUserService.getCurrentUser());
        if (avocat.isPresent()) {
            return PageResponse.of(demandeExplicationRepository
                    .findByAvocatIdOrderByDateDemandeDesc(avocat.get().getId(), pageable), this::versReponse);
        }
        return PageResponse.of(enAttenteSeulement
                ? demandeExplicationRepository.findByDateReponseIsNullOrderByDateDemandeDesc(pageable)
                : demandeExplicationRepository.findByOrderByDateDemandeDesc(pageable), this::versReponse);
    }

    /** Lawyers with a request still awaiting an answer: reminders are suspended for them. */
    @Transactional(readOnly = true)
    public List<Long> avocatsDontOnAttendUneExplication() {
        return demandeExplicationRepository.avocatsEnAttenteDeReponse();
    }

    private Avocat avocatOuErreur(Long avocatId) {
        return intervenantRepository.findById(avocatId)
                .map(Hibernate::unproxy)
                .filter(Avocat.class::isInstance)
                .map(Avocat.class::cast)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Aucun avocat du référentiel ne porte l'identifiant " + avocatId));
    }

    /** The lawyer linked to the logged-in account; an explicit 403 rather than a misleading empty list. */
    private Avocat avocatCourant() {
        return compteAvocatService.avocatDe(currentUserService.getCurrentUser())
                .orElseThrow(() -> ApiException.forbidden(ErrorCodes.AVOCAT_NON_RATTACHE,
                        "Aucun avocat du référentiel n'est rattaché à ce compte"));
    }

    private DemandeExplicationResponse versReponse(DemandeExplication demande) {
        Dossier dossier = demande.getDossier();
        Utilisateur auteur = demande.getAuteur();
        return new DemandeExplicationResponse(
                demande.getId(),
                demande.getAvocat().getId(),
                demande.getAvocat().getNom(),
                dossier == null ? null : dossier.getId(),
                dossier == null ? null : dossier.getReference(),
                auteur == null ? null : (auteur.getPrenom() + " " + auteur.getNom()).trim(),
                demande.getContenu(),
                demande.getDateDemande(),
                demande.getReponse(),
                demande.getDateReponse(),
                demande.estEnAttente());
    }
}
