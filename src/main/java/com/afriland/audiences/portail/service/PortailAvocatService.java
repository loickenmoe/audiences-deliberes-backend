package com.afriland.audiences.portail.service;

import com.afriland.audiences.audience.repository.AudienceRepository;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.ged.service.ValidationFichierService;
import com.afriland.audiences.intervenant.mapper.DemandeConstitutionMapper;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import com.afriland.audiences.portail.web.dto.AudienceAvocatResponse;
import com.afriland.audiences.portail.web.dto.DossierAvocatResponse;
import com.afriland.audiences.portail.web.dto.PieceJustificativeResponse;
import com.afriland.audiences.publication.mapper.PublicationMapper;
import com.afriland.audiences.publication.domain.Publication;
import com.afriland.audiences.publication.domain.StatutPublication;
import com.afriland.audiences.publication.repository.CorrespondanceAvocatRepository;
import com.afriland.audiences.publication.repository.PublicationRepository;
import com.afriland.audiences.publication.web.dto.CorrespondanceResponse;
import com.afriland.audiences.publication.web.dto.PublicationResponse;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectures du portail Avocat (Q-96, QF-01 du frontend).
 *
 * <p><b>Pourquoi ce module existe.</b> L'avocat ne porte pas {@code ROLE_CONSULTATION} : il
 * n'atteignait que six endpoints, tous en écriture ou propres à ses notifications. Or US 4.1 le fait
 * soumettre un {@code dossierId} et US 5.1 lui demande de déposer un compte rendu « <b>en
 * choisissant le dossier et l'audience</b> » — un choix que rien ne lui permettait de faire. Les deux
 * premières lectures ci-dessous comblent cette exigence.
 *
 * <p><b>Périmètre ajouté, à confirmer.</b> Les deux dernières — ses publications et ses lettres de
 * constitution — ne répondent à aucune exigence écrite : US 5.4 vise le juriste. Elles sont livrées
 * sur arbitrage du porteur du projet (2026-09-14), au motif que l'avocat est notifié du rejet d'une
 * publication sans pouvoir relire ce qui a été rejeté. <b>À confirmer avec la Direction Juridique.</b>
 *
 * <p><b>Principe de cadrage.</b> Chaque lecture est restreinte <b>dans la requête</b>, jamais par un
 * filtre appliqué après coup : un filtre oublié exposerait le portefeuille entier de la banque à un
 * prestataire externe. Les réponses sont réduites à ce que l'usage exige.
 */
@Service
@RequiredArgsConstructor
public class PortailAvocatService {

    private final CurrentUserService currentUserService;
    private final CompteAvocatService compteAvocatService;
    private final DossierRepository dossierRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final AudienceRepository audienceRepository;
    private final PublicationRepository publicationRepository;
    private final CorrespondanceAvocatRepository correspondanceAvocatRepository;
    private final PublicationMapper publicationMapper;
    private final DemandeConstitutionRepository demandeConstitutionRepository;
    private final DemandeConstitutionMapper demandeConstitutionMapper;
    private final MinioStorageService minioStorageService;
    private final ValidationFichierService validationFichierService;

    /** US 4.1, US 5.1 : de quoi désigner son dossier. Identification seule. */
    @Transactional(readOnly = true)
    public PageResponse<DossierAvocatResponse> mesDossiers(Pageable pageable) {
        return mesDossiers(null, pageable);
    }

    /**
     * @param reference fragment de référence cherché, ou {@code null} pour la liste entière. La
     *                  recherche est faite <b>par le serveur</b> : filtrer une page déjà chargée
     *                  n'aurait cherché que parmi les dossiers les plus récents, et aurait donc
     *                  répondu « aucun résultat » pour un dossier qui existe bel et bien
     */
    @Transactional(readOnly = true)
    public PageResponse<DossierAvocatResponse> mesDossiers(String reference, Pageable pageable) {
        Avocat avocat = avocatCourant();
        Page<Dossier> dossiers = reference == null || reference.isBlank()
                ? dossierRepository.findDossiersDeLAvocat(avocat.getId(), pageable)
                : dossierRepository.rechercherDossiersDeLAvocat(avocat.getId(), reference.strip(), pageable);
        return PageResponse.of(dossiers,
                dossier -> new DossierAvocatResponse(dossier.getId(), dossier.getReference(),
                        dossier.getNature(), dossier.getJuridictionSaisie()));
    }

    /**
     * US 5.1 : les audiences d'un de <b>ses</b> dossiers. Un dossier auquel il n'est pas affecté
     * répond 403 — et non 404 : le dossier existe, c'est l'accès qui est refusé.
     */
    @Transactional(readOnly = true)
    public List<AudienceAvocatResponse> audiencesDeMonDossier(Long dossierId) {
        Avocat avocat = avocatCourant();
        if (!dossierAvocatRepository.existsByDossierIdAndAvocatId(dossierId, avocat.getId())) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Vous n'êtes pas affecté à ce dossier");
        }
        /*
         * `unpaged` volontairement : cette liste alimente le **sélecteur d'audience** du formulaire
         * de dépôt (écran 33), pas un tableau. La paginer amputerait silencieusement le choix de
         * l'avocat — le compilateur a d'ailleurs signalé cet appel quand la requête est passée en
         * `Page`, ce qui a évité de le découvrir en recette.
         */
        return audienceRepository.findByDossierIdOrderByDatePlanifieeDesc(dossierId, Pageable.unpaged()).stream()
                .map(audience -> new AudienceAvocatResponse(audience.getId(), audience.getDatePlanifiee(),
                        audience.getDateTenue(), audience.getJuridiction(), audience.getStatut()))
                .toList();
    }

    /**
     * UC-INT-01 étape 4 — « l'avocat <b>charge</b> les pièces justificatives » (Q-97).
     *
     * <p>Le fichier est rangé sous un préfixe dédié et la <b>clé</b> est renvoyée : l'avocat la
     * reporte dans {@code piecesJustificatives} au moment de déposer sa demande. Deux temps plutôt
     * qu'un seul appel multipart : le contrat de {@code POST /frais/demandes} reste celui du
     * document d'API (JSON, tableau de chaînes), et un envoi qui échoue ne fait pas reperdre les
     * pièces déjà transmises.
     *
     * <p>Mêmes contrôles de format et de poids que la GED : le DJ pilote les deux par la même paire
     * de paramètres, {@code FORMATS_GED} et {@code TAILLE_MAX_GED}.
     */
    @Transactional(readOnly = true)
    public PieceJustificativeResponse deposerPieceJustificative(MultipartFile fichier) {
        avocatCourant();
        validationFichierService.valider(fichier);
        String cle = minioStorageService.deposerPieceFrais(fichier);
        return new PieceJustificativeResponse(cle, fichier.getOriginalFilename(),
                minioStorageService.urlTelechargement(cle));
    }

    /** Périmètre ajouté : ses propres dépôts, avec leur statut et le motif d'un rejet. */
    @Transactional(readOnly = true)
    public PageResponse<PublicationResponse> mesPublications(StatutPublication statut, Pageable pageable) {
        Avocat avocat = avocatCourant();
        var page = statut == null
                ? publicationRepository.findByAvocatId(avocat.getId(), pageable)
                : publicationRepository.findByAvocatIdAndStatutValidation(avocat.getId(), statut, pageable);

        /*
         * **La correspondance accompagne chaque publication** (QF-58). Elle en était absente : la
         * forme à un seul argument du mapper délègue à `toResponse(publication, null, null, null)`,
         * si bien que l'avocat recevait la notification d'une correspondance transmise sans qu'aucun
         * écran puisse la lui montrer.
         *
         * <p><b>Les commentaires restent absents, et c'est une règle, pas un oubli.</b> Le juriste ne
         * s'adresse jamais directement à l'avocat (Q-20, US 5.5, RG-INT-07) : il commente pour le
         * DJ/DJA, qui transmet **une** correspondance. Les joindre ici ouvrirait la délibération
         * interne à celui dont elle discute.
         */
        Map<Long, CorrespondanceResponse> correspondances = correspondancesDe(page.getContent().stream()
                .map(Publication::getId)
                .toList());

        return PageResponse.of(page, publication -> publicationMapper.toResponse(
                publication, null, null, correspondances.get(publication.getId())));
    }

    /** Les correspondances d'une page, en une lecture ; vide si la page l'est (QF-58). */
    private Map<Long, CorrespondanceResponse> correspondancesDe(List<Long> publicationIds) {
        if (publicationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, CorrespondanceResponse> parPublication = new HashMap<>();
        for (var correspondance : correspondanceAvocatRepository.findByPublicationIdIn(publicationIds)) {
            Long publicationId = correspondance.getPublication().getId();
            parPublication.put(publicationId, PublicationMapper.versReponse(publicationId, correspondance));
        }
        return parPublication;
    }

    /** Périmètre ajouté : les lettres de constitution qui le désignent. */
    @Transactional(readOnly = true)
    public List<DemandeConstitutionResponse> mesConstitutions() {
        Avocat avocat = avocatCourant();
        return demandeConstitutionRepository.findByPrestataireIdOrderByDateDemandeDesc(avocat.getId()).stream()
                .map(demandeConstitutionMapper::toResponse)
                .toList();
    }

    /**
     * L'avocat du référentiel rattaché au compte connecté. Un compte {@code ROLE_AVOCAT} sans
     * intervenant correspondant est une anomalie de paramétrage : mieux vaut un 403 explicite qu'une
     * liste vide, qui se lirait comme « vous n'avez aucun dossier ».
     */
    private Avocat avocatCourant() {
        return compteAvocatService.avocatDe(currentUserService.getCurrentUser())
                .orElseThrow(() -> ApiException.forbidden(ErrorCodes.AVOCAT_NON_RATTACHE,
                        "Aucun avocat du référentiel n'est rattaché à ce compte"));
    }
}
