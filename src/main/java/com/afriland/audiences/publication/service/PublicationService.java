package com.afriland.audiences.publication.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.audience.domain.Audience;
import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.DossierJuriste;
import com.afriland.audiences.dossier.repository.DossierJuristeRepository;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.ged.service.ValidationFichierService;
import com.afriland.audiences.publication.domain.AutrePublication;
import com.afriland.audiences.publication.domain.CommentairePublication;
import com.afriland.audiences.publication.domain.CompteRenduAudience;
import com.afriland.audiences.publication.domain.CorrespondanceAvocat;
import com.afriland.audiences.publication.domain.Publication;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.domain.TypeAutrePublication;
import com.afriland.audiences.publication.mapper.PublicationMapper;
import com.afriland.audiences.publication.repository.CommentairePublicationRepository;
import com.afriland.audiences.publication.repository.CorrespondanceAvocatRepository;
import com.afriland.audiences.publication.repository.PublicationRepository;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.publication.web.dto.CommentaireRequest;
import com.afriland.audiences.publication.web.dto.CommentaireResponse;
import com.afriland.audiences.publication.web.dto.CorrespondanceRequest;
import com.afriland.audiences.publication.web.dto.CorrespondanceResponse;
import com.afriland.audiences.publication.web.dto.DeposerCompteRenduRequest;
import com.afriland.audiences.publication.web.dto.PublicationResponse;
import com.afriland.audiences.publication.web.dto.ValiderPublicationRequest;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import com.afriland.audiences.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Epic 5, publications (FR-INT-09, RG-INT-06/07/09/10). Appointments and the lawyer directory live
 * in the {@code intervenant} module; this module does not depend on it, since no formal appointment
 * is involved in this workflow.
 */
@Service
@RequiredArgsConstructor
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final CommentairePublicationRepository commentairePublicationRepository;
    private final CorrespondanceAvocatRepository correspondanceAvocatRepository;
    private final DossierRepository dossierRepository;
    private final AudienceRepository audienceRepository;
    private final CompteAvocatService compteAvocatService;
    private final NotificationPrestataireService notificationPrestataireService;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserService currentUserService;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final MinioStorageService minioStorageService;
    // Same format and size rules as the GED for a hearing report attachment.
    private final ValidationFichierService validationFichierService;
    private final PublicationMapper publicationMapper;
    private final DossierJuristeRepository dossierJuristeRepository;

    /**
     * Profiles that handle a publication: the Assistant validates it (RG-INT-10), the DJ and DJA
     * centralise exchanges with the lawyer (RG-INT-07). The access restriction, which designates a
     * reader (RG-INT-06), does not apply to them.
     */
    private static final Set<String> PROFILS_INSTRUCTEURS = Set.of("ASSISTANTE", "DJ", "DJA");

    /** FR-INT-09/US 5.1: the hearing must belong to the given case file (404 otherwise). */
    @Transactional
    public PublicationResponse deposerCompteRendu(DeposerCompteRenduRequest request, MultipartFile file) {
        Dossier dossier = getDossierOrThrow(request.dossierId());
        Audience audience = audienceRepository.findById(request.audienceId())
                .filter(a -> a.getDossier().getId().equals(dossier.getId()))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Audience introuvable pour ce dossier : " + request.audienceId()));
        Avocat avocat = getAvocatCourantOrThrow();

        /*
         * RG-INT-06 (amended): a restriction no longer applies to a hearing report, which every authorised
         * user can read once filed. It is rejected rather than ignored: a lawyer setting it would believe
         * they had restricted access. It remains available on other publications.
         */
        if (request.restrictionAcces() != null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Un compte rendu d'audience ne se réserve pas à un destinataire : il est "
                            + "consultable par toute la Direction Juridique dès son dépôt.");
        }

        /*
         * Optional attachment: the report text remains mandatory, the document supports it. Format and
         * size checks are those of the GED, since the file lands in the same storage and the DJ controls
         * both through `FORMATS_GED` and `TAILLE_MAX_GED`.
         */
        String cle = null;
        if (file != null && !file.isEmpty()) {
            validationFichierService.valider(file);
            cle = minioStorageService.deposer(dossier.getId(), file);
        }

        CompteRenduAudience publication = CompteRenduAudience.builder()
                .dossier(dossier)
                .avocat(avocat)
                .audience(audience)
                .contenu(request.contenu())
                .cheminStockage(cle)
                .statutValidation(StatutPublication.DEPOSE)
                .build();
        publication = publicationRepository.save(publication);

        historiqueActionService.enregistrer(dossier.getId(), TypeActionHistorique.PUBLICATION_CR_DEPOSE,
                Map.of("publicationId", publication.getId(), "audienceId", audience.getId(),
                        "pieceJointe", cle != null));
        notifierProfil("ASSISTANTE", dossier.getId(), "Nouveau compte rendu d'audience déposé sur le dossier "
                + dossier.getReference() + " (RG-INT-09), en attente de validation.");

        // RG-INT-06 (amended): the case file's legal officers are notified at the same time as the
        // Assistant and can read the report without waiting for validation; without the alert they
        // would only find it by chance.
        notifierLecteurs(publication, "Nouveau compte rendu d'audience de l'avocat sur le dossier "
                + dossier.getReference() + ", consultable dès maintenant ; il reste en attente de "
                + "validation par l'Assistante (RG-INT-06).");

        return publicationMapper.toResponse(publication);
    }

    /** FR-INT-09/US 5.3: no format/size constraint specified (unlike RG-GED-02); stored as is. */
    @Transactional
    public PublicationResponse deposerAutre(Long dossierId, TypeAutrePublication typeDocument, MultipartFile file, Long restrictionAcces) {
        Dossier dossier = getDossierOrThrow(dossierId);
        Avocat avocat = getAvocatCourantOrThrow();
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Le fichier de la publication est obligatoire");
        }
        String cle = minioStorageService.deposer(dossierId, file);

        AutrePublication publication = AutrePublication.builder()
                .dossier(dossier)
                .avocat(avocat)
                .typeDocument(typeDocument)
                .cheminStockage(cle)
                .statutValidation(StatutPublication.DEPOSE)
                .restrictionAcces(resoudreRestrictionAcces(restrictionAcces))
                .build();
        publication = publicationRepository.save(publication);

        historiqueActionService.enregistrer(dossier.getId(), TypeActionHistorique.PUBLICATION_AUTRE_DEPOSEE,
                Map.of("publicationId", publication.getId(), "typeDocument", typeDocument.name()));
        notifierProfil("ASSISTANTE", dossier.getId(), "Nouvelle publication (" + typeDocument
                + ") déposée sur le dossier " + dossier.getReference() + " (RG-INT-06), en attente de validation.");

        return publicationMapper.toResponse(publication);
    }

    /**
     * FR-INT-09/US 5.2: the contract defines a single validation endpoint
     * ({@code PUT /publications/cr-audience/{id}/validation}), but US 5.3 publications also require the
     * Assistant's decision and RG-INT-06/10 apply to every {@code publication}. Handled as a single
     * entry point for both subtypes.
     */
    @Transactional
    public PublicationResponse valider(Long id, ValiderPublicationRequest request) {
        Publication publication = getPublicationOrThrow(id);
        if (publication.getStatutValidation() != StatutPublication.DEPOSE) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Cette publication a déjà été traitée");
        }

        if (request.statut() == StatutPublication.VALIDE) {
            publication.setStatutValidation(StatutPublication.VALIDE);
            // A hearing report was already announced to the case file's legal officers when filed
            // (RG-INT-06 amended): notifying them again would add nothing and devalue the alert.
            // Validation opens it to the OTHER authorised users, who were not notified before.
            if (!(publication instanceof CompteRenduAudience)) {
                notifierLecteurs(publication, "Nouvelle publication de l'avocat consultable sur le dossier "
                        + publication.getDossier().getReference() + " (RG-INT-06).");
            }
        } else if (request.statut() == StatutPublication.REJETE) {
            /*
             * RG-INT-06 (amended): a hearing report cannot be rejected.
             *
             * It is the lawyer's account of the hearing and is readable by all once filed. Rejecting it
             * would erase nothing and suggest the hearing was not reported. Disagreement on its content goes
             * through a correspondence (RG-INT-07), the only official channel to the lawyer.
             *
             * Rejection remains for other publications, whose compliance is checked.
             */
            if (publication instanceof CompteRenduAudience) {
                throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                        "Un compte rendu d'audience ne se rejette pas : l'Assistante en accuse "
                                + "réception. Un désaccord se transmet par une correspondance.");
            }
            if (!StringUtils.hasText(request.motifRejet())) {
                throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Le motif de rejet est obligatoire");
            }
            publication.setStatutValidation(StatutPublication.REJETE);
            publication.setMotifRejet(request.motifRejet());
            compteAvocatService.utilisateurDe(publication.getAvocat())
                    .ifPresent(u -> alerteService.declencher(TypeAlerte.NOUVELLE_PUBLICATION, publication.getDossier().getId(), u,
                            "Votre publication " + id + " sur le dossier " + publication.getDossier().getReference()
                                    + " a été rejetée : " + request.motifRejet()));
        } else {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Statut de validation invalide : " + request.statut());
        }
        publicationRepository.save(publication);

        /*
         * External channels: the lawyer is notified, and the rejection reason stays in the application.
         *
         * Two distinct events: on a hearing report the Assistant made no decision, she only acknowledged
         * receipt (RG-INT-06 amended). Announcing a "reviewed" publication would make the lawyer wait for
         * a verdict.
         */
        boolean compteRendu = publication instanceof CompteRenduAudience;
        notificationPrestataireService.notifier(
                compteRendu ? EvenementPrestataire.COMPTE_RENDU_RECU
                            : EvenementPrestataire.PUBLICATION_ARBITREE,
                publication.getAvocat(), publication.getDossier().getId(),
                "publication-" + id + (compteRendu ? "-reception" : "-arbitrage"));

        historiqueActionService.enregistrer(publication.getDossier().getId(), TypeActionHistorique.PUBLICATION_ARBITREE,
                Map.of("publicationId", id, "statut", request.statut().name()));

        return publicationMapper.toResponse(publication);
    }

    /**
     * US 5.4: only a VALIDE publication is readable, with an optional individual access restriction.
     * <p>
     * The Assistant can also read a DEPOSE or REJETE publication, since she must see what she validates
     * (UC-INT-10 step 3); the access restriction does not apply to investigating profiles. The detail
     * includes the presigned URL of an other publication's file, the legal officers' comments and the
     * correspondence sent.
     */
    @Transactional(readOnly = true)
    public PublicationResponse obtenir(Long id) {
        Publication publication = getPublicationOrThrow(id);
        boolean valide = publication.getStatutValidation() == StatutPublication.VALIDE;
        Utilisateur restriction = publication.getRestrictionAcces();
        /*
         * RG-INT-06 (amended): a HEARING REPORT is open to all authorised users once filed. Neither its
         * status nor a restriction closes it; the only remaining barrier is the consultation role,
         * enforced by the controller. The Assistant's subsequent action is an acknowledgement, not an
         * authorisation.
         *
         * Other publications keep the rules below.
         */
        if (publication instanceof CompteRenduAudience) {
            valide = true;
            restriction = null;
        }
        if (!valide || restriction != null) {
            Utilisateur utilisateur = currentUserService.getCurrentUser();
            String profil = utilisateur != null && utilisateur.getProfil() != null ? utilisateur.getProfil().getCode() : null;
            if (!valide && !"ASSISTANTE".equals(profil)) {
                throw ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Publication introuvable ou non consultable : " + id);
            }
            // Set.of(...).contains(null) throws: an unknown profile does not investigate.
            boolean instructeur = profil != null && PROFILS_INSTRUCTEURS.contains(profil);
            if (restriction != null && !instructeur
                    && (utilisateur == null || !restriction.getId().equals(utilisateur.getId()))) {
                throw ApiException.forbidden(ErrorCodes.ACCES_RESTREINT_PUBLICATION,
                        "Cette publication est restreinte à un autre destinataire");
            }
        }

        // Both subtypes can carry a file (optional for a report, inherent for an other publication).
        // The Assistant must open it to validate.
        String cheminStockage = switch (publication) {
            case AutrePublication autre -> autre.getCheminStockage();
            case CompteRenduAudience cr -> cr.getCheminStockage();
            default -> null;
        };
        String url = cheminStockage != null ? minioStorageService.urlTelechargement(cheminStockage) : null;
        List<CommentaireResponse> commentaires = commentairePublicationRepository.findByPublicationIdOrderByCreatedAtAsc(id)
                .stream()
                .map(commentaire -> versReponse(id, commentaire))
                .toList();
        CorrespondanceResponse correspondance = correspondanceAvocatRepository.findByPublicationId(id)
                .map(c -> PublicationMapper.versReponse(id, c))
                .orElse(null);
        return publicationMapper.toResponse(publication, url, commentaires, correspondance);
    }

    /** US 5.5/RG-INT-07: the legal officer never comments directly to the lawyer; the comment goes to the DJ/DJA. */
    @Transactional
    public CommentaireResponse commenter(Long id, CommentaireRequest request) {
        Publication publication = getPublicationOrThrow(id);
        Utilisateur juriste = currentUserService.getCurrentUser();

        CommentairePublication commentaire = commentairePublicationRepository.save(CommentairePublication.builder()
                .publication(publication)
                .auteur(juriste)
                .contenu(request.contenu())
                .build());

        historiqueActionService.enregistrer(publication.getDossier().getId(), TypeActionHistorique.PUBLICATION_COMMENTEE,
                Map.of("publicationId", id, "commentaireId", commentaire.getId()));
        notifierProfil("DJ", publication.getDossier().getId(), "Nouveau commentaire du juriste sur la publication "
                + id + " du dossier " + publication.getDossier().getReference() + " (RG-INT-07).");
        notifierProfil("DJA", publication.getDossier().getId(), "Nouveau commentaire du juriste sur la publication "
                + id + " du dossier " + publication.getDossier().getReference() + " (RG-INT-07).");

        return versReponse(id, commentaire);
    }

    /** US 5.5/RG-INT-07: single correspondence per publication (409 if already sent). */
    @Transactional
    public CorrespondanceResponse transmettreCorrespondance(Long id, CorrespondanceRequest request) {
        Publication publication = getPublicationOrThrow(id);
        if (correspondanceAvocatRepository.existsByPublicationId(id)) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Une correspondance a déjà été transmise pour cette publication");
        }
        Utilisateur auteur = currentUserService.getCurrentUser();

        CorrespondanceAvocat correspondance = correspondanceAvocatRepository.save(CorrespondanceAvocat.builder()
                .publication(publication)
                .auteur(auteur)
                .contenu(request.contenu())
                .build());

        historiqueActionService.enregistrer(publication.getDossier().getId(), TypeActionHistorique.PUBLICATION_CORRESPONDANCE_TRANSMISE,
                Map.of("publicationId", id, "correspondanceId", correspondance.getId()));
        compteAvocatService.utilisateurDe(publication.getAvocat())
                .ifPresent(u -> alerteService.declencher(TypeAlerte.NOUVELLE_PUBLICATION, publication.getDossier().getId(), u,
                        "Une correspondance a été transmise concernant votre publication " + id + " (RG-INT-07)."));
        notificationPrestataireService.notifier(EvenementPrestataire.CORRESPONDANCE_TRANSMISE, publication.getAvocat(),
                publication.getDossier().getId(), "correspondance-" + correspondance.getId());

        return PublicationMapper.versReponse(id, correspondance);
    }

    /**
     * Publications queue: the Assistant's "publications to validate" screen ({@code statut=DEPOSE})
     * and the publications tab of a case file.
     * <p>
     * Unlike {@link #obtenir(Long)}, this list does <b>not</b> apply the individual access restriction:
     * it serves internal profiles who handle publications and only exposes metadata; detailed content
     * remains subject to the per-item check.
     */
    @Transactional(readOnly = true)
    public PageResponse<PublicationResponse> lister(Long dossierId, StatutPublication statut, Pageable pageable) {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        String profil = utilisateur != null && utilisateur.getProfil() != null
                ? utilisateur.getProfil().getCode() : null;

        /*
         * RG-INT-06 (amended): the list decides what it shows. A restriction left to the caller is no
         * restriction, and the two filing types no longer share the same visibility (report open once
         * filed, other publication hidden until validated), so the rule is enforced here.
         */
        if (profil != null && PROFILS_INSTRUCTEURS.contains(profil)) {
            return PageResponse.of(listerSansRestriction(dossierId, statut, pageable),
                    publicationMapper::toResponse);
        }
        return PageResponse.of(
                publicationRepository.findVisiblesParLesHabilites(dossierId, statut, pageable),
                publicationMapper::toResponse);
    }

    /** The Assistant's queue and the DJ/DJA view: no visibility filter. */
    private Page<Publication> listerSansRestriction(Long dossierId, StatutPublication statut, Pageable pageable) {
        if (dossierId != null && statut != null) {
            return publicationRepository.findByDossierIdAndStatutValidation(dossierId, statut, pageable);
        }
        if (dossierId != null) {
            return publicationRepository.findByDossierId(dossierId, pageable);
        }
        if (statut != null) {
            return publicationRepository.findByStatutValidation(statut, pageable);
        }
        return publicationRepository.findAll(pageable);
    }


    /**
     * UC-INT-08 step 1, alt. 3-a: a validated publication is announced to the case file's legal
     * officers, or only to the designated recipient if the lawyer restricted access.
     */
    private void notifierLecteurs(Publication publication, String declencheur) {
        Dossier dossier = publication.getDossier();
        List<Utilisateur> lecteurs = publication.getRestrictionAcces() != null
                ? List.of(publication.getRestrictionAcces())
                : dossierJuristeRepository.findByDossierId(dossier.getId()).stream()
                        .map(DossierJuriste::getUtilisateur)
                        .toList();
        lecteurs.forEach(lecteur -> alerteService.declencher(TypeAlerte.NOUVELLE_PUBLICATION, dossier.getId(), lecteur, declencheur));
    }

    private static CommentaireResponse versReponse(Long publicationId, CommentairePublication commentaire) {
        Utilisateur auteur = commentaire.getAuteur();
        return new CommentaireResponse(commentaire.getId(), publicationId, auteur != null ? auteur.getId() : null,
                PublicationMapper.nomComplet(auteur), commentaire.getContenu(), commentaire.getCreatedAt());
    }

    private Utilisateur resoudreRestrictionAcces(Long utilisateurId) {
        if (utilisateurId == null) {
            return null;
        }
        return utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Utilisateur introuvable : " + utilisateurId));
    }

    private void notifierProfil(String profilCode, Long dossierId, String declencheur) {
        List<Utilisateur> destinataires = utilisateurRepository.findByProfilCodeAndActifTrue(profilCode);
        destinataires.forEach(destinataire -> alerteService.declencher(TypeAlerte.NOUVELLE_PUBLICATION, dossierId, destinataire, declencheur));
    }

    private Avocat getAvocatCourantOrThrow() {
        Utilisateur utilisateur = currentUserService.getCurrentUser();
        return compteAvocatService.avocatDe(utilisateur)
                .orElseThrow(() -> ApiException.forbidden(ErrorCodes.AVOCAT_NON_RATTACHE,
                        "Aucun avocat du référentiel n'est rattaché à ce compte"));
    }

    private Publication getPublicationOrThrow(Long id) {
        return publicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Publication introuvable : " + id));
    }

    private Dossier getDossierOrThrow(Long id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + id));
    }
}
