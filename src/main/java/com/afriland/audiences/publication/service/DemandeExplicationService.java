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
 * La demande d'explication, et la réponse de l'avocat (N7 temps 2, observation 8).
 *
 * <p><b>Ce que ce service ajoute au constat.</b> Le planificateur signale à la direction qu'un
 * avocat a manqué deux audiences de suite ; il s'arrête là, et c'est voulu - l'application constate,
 * elle ne sanctionne pas. Ce service porte ce qui vient ensuite : un responsable écrit, l'avocat
 * répond, et l'échange reste. Sans cela, la direction devait retenir de tête qui s'était expliqué.
 *
 * <p><b>Une demande suspend la relance automatique.</b> Tant qu'elle attend sa réponse, le rappel
 * tous les deux jours se tait pour cet avocat : un responsable qui écrit en personne doit couvrir
 * la machine, pas rivaliser avec elle. La relance reprend à la réponse - le compte rendu manque
 * toujours, et c'est de nouveau l'affaire du rappel automatique.
 *
 * <p><b>Ce qui n'est pas fait, et pourquoi.</b> Aucune relance de la demande elle-même : « reprendre
 * si rien n'arrive » supposerait un délai que personne n'a fixé, et je n'en invente pas. Une demande
 * sans réponse reste visible dans la file de la direction, qui décide d'insister ou non.
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
     * La direction demande à un avocat de s'expliquer.
     *
     * <p><b>Une demande en attente à la fois.</b> En ouvrir une seconde pendant que la première
     * attend ne dirait rien de plus à l'avocat et brouillerait la lecture de la file : laquelle
     * suspend la relance, à laquelle faut-il répondre ? Une fois répondu, la direction peut
     * redemander.
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

        // Dans l'application, pour qu'il la voie en se connectant ; et au dehors, parce qu'il peut
        // justement ne plus s'y connecter - c'est le reproche qu'on lui fait.
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
     * L'avocat répond, et une seule fois.
     *
     * <p>Une réponse qu'on pourrait récrire ne prouverait rien : c'est une prise de position, datée,
     * que la direction lira peut-être des mois plus tard. Corriger suppose une nouvelle demande.
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

        // Celui qui a demandé doit savoir qu'on lui a répondu : sans cela il faudrait qu'il pense
        // à revenir voir.
        alerteService.declencher(TypeAlerte.DEMANDE_EXPLICATION,
                demande.getDossier() == null ? null : demande.getDossier().getId(),
                demande.getAuteur(),
                "L'avocat " + demande.getAvocat().getNom() + " a répondu à votre demande d'explication "
                        + demande.getId() + ".");

        return versReponse(demande);
    }

    /**
     * La file de la direction, ou celle de l'avocat.
     *
     * <p>Le périmètre se décide <b>ici</b>, sur l'identité de qui lit, et non par un paramètre que
     * l'écran enverrait : un avocat qui demanderait la liste entière la recevrait.
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

    /** Les avocats dont une demande attend encore une réponse : la relance se tait pour eux. */
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

    /** L'avocat rattaché au compte connecté - 403 explicite plutôt qu'une liste vide trompeuse. */
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
