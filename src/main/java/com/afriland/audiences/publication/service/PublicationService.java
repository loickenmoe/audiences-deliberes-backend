package com.afriland.audiences.publication.service;

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
 * Épic 5, volet publications (FR-INT-09, RG-INT-06/07/09/10) — cf. FUNCTIONAL_MAP.md §5,
 * API_IMPLEMENTATION_STATUS.md §6. Le volet constitutions/répertoire (M9) vit dans le module
 * {@code intervenant} ; celui-ci ne dépend pas de {@code intervenant} (mêmes raisons que Q-54 pour
 * {@code frais} : aucune notion de constitution formelle n'intervient dans ce workflow).
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
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserService currentUserService;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final MinioStorageService minioStorageService;
    private final PublicationMapper publicationMapper;
    private final DossierJuristeRepository dossierJuristeRepository;

    /**
     * Q-86 : profils qui instruisent une publication — l'Assistante la valide (RG-INT-10), le DJ et
     * la DJA centralisent les échanges avec l'avocat (RG-INT-07). La restriction d'accès, qui désigne
     * un juriste lecteur (RG-INT-06), ne s'oppose pas à eux.
     */
    private static final Set<String> PROFILS_INSTRUCTEURS = Set.of("ASSISTANTE", "DJ", "DJA");

    /** FR-INT-09/US 5.1 : l'audience doit être rattachée au dossier indiqué (404 sinon). */
    @Transactional
    public PublicationResponse deposerCompteRendu(DeposerCompteRenduRequest request) {
        Dossier dossier = getDossierOrThrow(request.dossierId());
        Audience audience = audienceRepository.findById(request.audienceId())
                .filter(a -> a.getDossier().getId().equals(dossier.getId()))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Audience introuvable pour ce dossier : " + request.audienceId()));
        Avocat avocat = getAvocatCourantOrThrow();

        CompteRenduAudience publication = CompteRenduAudience.builder()
                .dossier(dossier)
                .avocat(avocat)
                .audience(audience)
                .contenu(request.contenu())
                .statutValidation(StatutPublication.DEPOSE)
                .restrictionAcces(resoudreRestrictionAcces(request.restrictionAcces()))
                .build();
        publication = publicationRepository.save(publication);

        historiqueActionService.enregistrer(dossier.getId(), "Dépôt d'un compte rendu d'audience",
                Map.of("publicationId", publication.getId(), "audienceId", audience.getId()));
        notifierProfil("ASSISTANTE", dossier.getId(), "Nouveau compte rendu d'audience déposé sur le dossier "
                + dossier.getReference() + " (RG-INT-09), en attente de validation.");

        return publicationMapper.toResponse(publication);
    }

    /** FR-INT-09/US 5.3 : aucune contrainte de format/poids spécifiée (contrairement à RG-GED-02) — dépôt tel quel. */
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

        historiqueActionService.enregistrer(dossier.getId(), "Dépôt d'une autre publication",
                Map.of("publicationId", publication.getId(), "typeDocument", typeDocument.name()));
        notifierProfil("ASSISTANTE", dossier.getId(), "Nouvelle publication (" + typeDocument
                + ") déposée sur le dossier " + dossier.getReference() + " (RG-INT-06), en attente de validation.");

        return publicationMapper.toResponse(publication);
    }

    /**
     * FR-INT-09/US 5.2 : le contrat ne numérote qu'un endpoint de validation
     * ({@code PUT /publications/cr-audience/{id}/validation}) alors que US 5.3 dépose aussi des
     * publications nécessitant une décision de l'Assistante ; RG-INT-06/10 s'appliquent uniformément
     * à la {@code publication} de base — traité comme un point d'entrée unique, valable pour les deux
     * sous-types, plutôt que d'inventer un second endpoint non prévu pour les « autres » publications.
     */
    @Transactional
    public PublicationResponse valider(Long id, ValiderPublicationRequest request) {
        Publication publication = getPublicationOrThrow(id);
        if (publication.getStatutValidation() != StatutPublication.DEPOSE) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Cette publication a déjà été traitée");
        }

        if (request.statut() == StatutPublication.VALIDE) {
            publication.setStatutValidation(StatutPublication.VALIDE);
            notifierLecteurs(publication);
        } else if (request.statut() == StatutPublication.REJETE) {
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

        historiqueActionService.enregistrer(publication.getDossier().getId(), "Décision sur une publication",
                Map.of("publicationId", id, "statut", request.statut().name()));

        return publicationMapper.toResponse(publication);
    }

    /**
     * US 5.4 : seule une publication VALIDE est consultable ; restriction d'accès individuelle possible.
     * <p>
     * Q-86 : l'Assistante lit aussi une publication DEPOSE ou REJETE — elle doit voir ce qu'elle valide
     * (UC-INT-10 étape 3) ; la restriction d'accès ne s'oppose pas aux profils instructeurs. Le détail
     * porte l'URL pré-signée du fichier d'une autre publication, les commentaires des juristes et la
     * correspondance transmise.
     */
    @Transactional(readOnly = true)
    public PublicationResponse obtenir(Long id) {
        Publication publication = getPublicationOrThrow(id);
        boolean valide = publication.getStatutValidation() == StatutPublication.VALIDE;
        Utilisateur restriction = publication.getRestrictionAcces();
        if (!valide || restriction != null) {
            Utilisateur utilisateur = currentUserService.getCurrentUser();
            String profil = utilisateur != null && utilisateur.getProfil() != null ? utilisateur.getProfil().getCode() : null;
            if (!valide && !"ASSISTANTE".equals(profil)) {
                throw ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Publication introuvable ou non consultable : " + id);
            }
            // Set.of(...).contains(null) lève une exception : un profil inconnu n'instruit pas.
            boolean instructeur = profil != null && PROFILS_INSTRUCTEURS.contains(profil);
            if (restriction != null && !instructeur
                    && (utilisateur == null || !restriction.getId().equals(utilisateur.getId()))) {
                throw ApiException.forbidden(ErrorCodes.ACCES_RESTREINT_PUBLICATION,
                        "Cette publication est restreinte à un autre destinataire");
            }
        }

        String url = publication instanceof AutrePublication autre && autre.getCheminStockage() != null
                ? minioStorageService.urlTelechargement(autre.getCheminStockage())
                : null;
        List<CommentaireResponse> commentaires = commentairePublicationRepository.findByPublicationIdOrderByCreatedAtAsc(id)
                .stream()
                .map(commentaire -> versReponse(id, commentaire))
                .toList();
        CorrespondanceResponse correspondance = correspondanceAvocatRepository.findByPublicationId(id)
                .map(c -> versReponse(id, c))
                .orElse(null);
        return publicationMapper.toResponse(publication, url, commentaires, correspondance);
    }

    /** US 5.5/RG-INT-07 : le juriste ne commente jamais directement l'avocat — le commentaire est transmis au DJ/DJA. */
    @Transactional
    public CommentaireResponse commenter(Long id, CommentaireRequest request) {
        Publication publication = getPublicationOrThrow(id);
        Utilisateur juriste = currentUserService.getCurrentUser();

        CommentairePublication commentaire = commentairePublicationRepository.save(CommentairePublication.builder()
                .publication(publication)
                .auteur(juriste)
                .contenu(request.contenu())
                .build());

        historiqueActionService.enregistrer(publication.getDossier().getId(), "Commentaire sur une publication",
                Map.of("publicationId", id, "commentaireId", commentaire.getId()));
        notifierProfil("DJ", publication.getDossier().getId(), "Nouveau commentaire du juriste sur la publication "
                + id + " du dossier " + publication.getDossier().getReference() + " (RG-INT-07).");
        notifierProfil("DJA", publication.getDossier().getId(), "Nouveau commentaire du juriste sur la publication "
                + id + " du dossier " + publication.getDossier().getReference() + " (RG-INT-07).");

        return versReponse(id, commentaire);
    }

    /** US 5.5/RG-INT-07 : correspondance unique par publication (409 si déjà transmise). */
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

        historiqueActionService.enregistrer(publication.getDossier().getId(), "Correspondance transmise à l'avocat",
                Map.of("publicationId", id, "correspondanceId", correspondance.getId()));
        compteAvocatService.utilisateurDe(publication.getAvocat())
                .ifPresent(u -> alerteService.declencher(TypeAlerte.NOUVELLE_PUBLICATION, publication.getDossier().getId(), u,
                        "Une correspondance a été transmise concernant votre publication " + id + " (RG-INT-07)."));

        return versReponse(id, correspondance);
    }

    /**
     * M15 (Q-68) : file de travail des publications — écran « File des publications à valider » de
     * l'Assistante (filtre {@code statut=DEPOSE}) et onglet publications d'un dossier.
     * <p>
     * Contrairement à {@link #obtenir(Long)}, cette liste **n'applique pas** la restriction d'accès
     * individuelle (Q-57) : elle sert des profils internes chargés d'instruire les publications, et
     * n'expose que les métadonnées — le contenu détaillé reste soumis au contrôle unitaire.
     */
    @Transactional(readOnly = true)
    public PageResponse<PublicationResponse> lister(Long dossierId, StatutPublication statut, Pageable pageable) {
        Page<Publication> page;
        if (dossierId != null && statut != null) {
            page = publicationRepository.findByDossierIdAndStatutValidation(dossierId, statut, pageable);
        } else if (dossierId != null) {
            page = publicationRepository.findByDossierId(dossierId, pageable);
        } else if (statut != null) {
            page = publicationRepository.findByStatutValidation(statut, pageable);
        } else {
            page = publicationRepository.findAll(pageable);
        }
        return PageResponse.of(page, publicationMapper::toResponse);
    }

    /**
     * Q-86 (UC-INT-08 étape 1, alt. 3-a) : une publication validée est signalée aux juristes du
     * dossier — au seul destinataire désigné si l'avocat en a restreint l'accès.
     */
    private void notifierLecteurs(Publication publication) {
        Dossier dossier = publication.getDossier();
        List<Utilisateur> lecteurs = publication.getRestrictionAcces() != null
                ? List.of(publication.getRestrictionAcces())
                : dossierJuristeRepository.findByDossierId(dossier.getId()).stream()
                        .map(DossierJuriste::getUtilisateur)
                        .toList();
        String declencheur = "Nouvelle publication de l'avocat consultable sur le dossier "
                + dossier.getReference() + " (RG-INT-06).";
        lecteurs.forEach(lecteur -> alerteService.declencher(TypeAlerte.NOUVELLE_PUBLICATION, dossier.getId(), lecteur, declencheur));
    }

    private static CommentaireResponse versReponse(Long publicationId, CommentairePublication commentaire) {
        Utilisateur auteur = commentaire.getAuteur();
        return new CommentaireResponse(commentaire.getId(), publicationId, auteur != null ? auteur.getId() : null,
                PublicationMapper.nomComplet(auteur), commentaire.getContenu(), commentaire.getCreatedAt());
    }

    private static CorrespondanceResponse versReponse(Long publicationId, CorrespondanceAvocat correspondance) {
        Utilisateur auteur = correspondance.getAuteur();
        return new CorrespondanceResponse(correspondance.getId(), publicationId, auteur != null ? auteur.getId() : null,
                PublicationMapper.nomComplet(auteur), correspondance.getContenu(), correspondance.getCreatedAt());
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
                .orElseThrow(() -> ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS, "Aucun avocat associé à cet utilisateur"));
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
