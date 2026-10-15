package com.afriland.audiences.ged.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.ged.domain.DecisionPartage;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.StatutPartageAvocat;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.web.dto.ArbitragePartageRequest;
import com.afriland.audiences.ged.web.dto.EtatPartageDossierResponse;
import com.afriland.audiences.ged.web.dto.PartageAvocatResponse;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Le partage d'une pièce de la GED avec l'avocat du dossier (QF-64).
 *
 * <p><b>Les règles, arbitrées par le porteur du projet le 2026-10-15.</b>
 * <ol>
 *   <li>Tout juriste partage une pièce, sans validation du DJ - <b>le SH et l'Assistante aussi</b>
 *       depuis QF-65, « ils sont à la base des juristes ».</li>
 *   <li>Sur un dossier <b>sensible</b>, le partage est réservé au DJ et à la DJA : le juriste, le SH
 *       ou l'Assistante le <b>propose</b>, et rien n'est visible de l'avocat avant leur validation. Le
 *       DJ ou la DJA qui partage lui-même n'a pas à se valider. La règle se lit sur le profil
 *       ({@link #DIRECTION}), si bien que l'ouverture aux deux nouveaux profils n'a rien demandé
 *       ici : ils proposent, comme tout profil qui n'est pas la direction.</li>
 *   <li>L'avocat ne voit une pièce partagée que s'il est affecté au dossier <b>et constitué</b> :
 *       c'est {@code PortailAvocatService} qui le vérifie, à chaque lecture.</li>
 * </ol>
 *
 * <p><b>Un dossier sensible se lit sur {@code estSensible}</b>, une proposition de sensibilité en
 * attente comprise : un rejet le repasse à faux. Dans le doute, le DJ valide - c'est le côté
 * prudent, et une pièce partagée à tort ne se reprend pas.
 *
 * <p><b>Service à part de {@code GedService}</b>, qui porte déjà quinze dépendances : le partage
 * est un circuit distinct du dépôt et de la suppression, et l'y greffer aurait alourdi un service
 * dont chaque modification touche toute la GED.
 */
@Service
@RequiredArgsConstructor
public class PartageDocumentService {

    /** Les profils qui tranchent sur un dossier sensible. Lus sur le profil, jamais sur le rôle (Q-81). */
    private static final Set<String> DIRECTION = Set.of("DJ", "DJA");

    private final DocumentRepository documentRepository;
    private final CurrentUserService currentUserService;
    private final HistoriqueActionService historiqueActionService;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final DemandeConstitutionRepository demandeConstitutionRepository;

    /**
     * Un avocat affecté au dossier y est-il constitué ? (QF-65)
     *
     * <p>La règle même qui ouvre les pièces à l'avocat (Q-139), évaluée ici pour celui qui
     * partage : sans elle, il lit « Partagé » quand l'avocat ne voit rien. Un dossier compte peu
     * d'avocats : une lecture par avocat affecté reste négligeable.
     */
    @Transactional(readOnly = true)
    public EtatPartageDossierResponse etat(Long dossierId) {
        boolean constitue = dossierAvocatRepository.findByDossierId(dossierId).stream()
                .anyMatch(affectation -> demandeConstitutionRepository.existsByDossierIdAndPrestataireIdAndStatut(
                        dossierId, affectation.getAvocat().getId(), StatutConstitution.VALIDEE));
        return new EtatPartageDossierResponse(constitue);
    }

    /**
     * Partage une pièce - ou, sur un dossier sensible, propose de la partager.
     *
     * @throws ApiException 409 si la pièce est déjà partagée ou en attente de validation
     */
    @Transactional
    public PartageAvocatResponse partager(Long documentId) {
        Document document = documentOuErreur(documentId);
        if (document.getPartageAvocat() != StatutPartageAvocat.NON) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "Cette pièce est déjà partagée, ou son partage attend la validation du DJ ou de la DJA");
        }
        Utilisateur auteur = currentUserService.getCurrentUser();
        boolean sensible = Boolean.TRUE.equals(document.getDossier().getEstSensible());
        boolean direction = estDirection(auteur);
        LocalDateTime maintenant = LocalDateTime.now();

        document.setPartagePar(auteur);
        document.setPartageLe(maintenant);
        TypeActionHistorique trace;
        if (sensible && !direction) {
            document.setPartageAvocat(StatutPartageAvocat.PROPOSE);
            trace = TypeActionHistorique.DOCUMENT_PARTAGE_PROPOSE;
        } else {
            document.setPartageAvocat(StatutPartageAvocat.PARTAGE);
            if (sensible) {
                // Le DJ ou la DJA qui partage valide du même geste : sa décision est la validation.
                document.setPartageValidePar(auteur);
                document.setPartageValideLe(maintenant);
            }
            trace = TypeActionHistorique.DOCUMENT_PARTAGE_AVOCAT;
        }
        document = documentRepository.save(document);
        historiqueActionService.enregistrer(document.getDossier().getId(), trace, Map.of("documentId", document.getId()));
        return versReponse(document);
    }

    /**
     * Retire un partage, ou annule une proposition. Ouvert à tout juriste, sur tout dossier :
     * retirer réduit l'exposition d'une pièce, et ne demande donc aucune validation.
     *
     * @throws ApiException 409 si la pièce n'est ni partagée ni proposée
     */
    @Transactional
    public PartageAvocatResponse retirer(Long documentId) {
        Document document = documentOuErreur(documentId);
        StatutPartageAvocat ancien = document.getPartageAvocat();
        if (ancien == StatutPartageAvocat.NON) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Cette pièce n'est pas partagée");
        }
        reinitialiser(document);
        document = documentRepository.save(document);
        historiqueActionService.enregistrer(document.getDossier().getId(), TypeActionHistorique.DOCUMENT_PARTAGE_RETIRE,
                Map.of("documentId", document.getId(), "ancienStatut", ancien.name()));
        return versReponse(document);
    }

    /**
     * Le DJ ou la DJA tranche un partage proposé sur un dossier sensible.
     *
     * @throws ApiException 409 si aucun partage n'attend de validation ; 400 sur un rejet sans motif
     */
    @Transactional
    public PartageAvocatResponse arbitrer(Long documentId, ArbitragePartageRequest request) {
        Document document = documentOuErreur(documentId);
        if (document.getPartageAvocat() != StatutPartageAvocat.PROPOSE) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Ce partage n'attend aucune validation");
        }
        String motif = request.motif() == null ? null : request.motif().trim();
        if (request.decision() == DecisionPartage.REJETE && (motif == null || motif.isEmpty())) {
            // Le juriste qui a proposé doit savoir pourquoi la pièce ne part pas.
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Le rejet d'un partage exige un motif");
        }

        if (request.decision() == DecisionPartage.VALIDE) {
            document.setPartageAvocat(StatutPartageAvocat.PARTAGE);
            document.setPartageValidePar(currentUserService.getCurrentUser());
            document.setPartageValideLe(LocalDateTime.now());
        } else {
            reinitialiser(document);
        }
        document = documentRepository.save(document);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("documentId", document.getId());
        details.put("decision", request.decision().name());
        if (request.decision() == DecisionPartage.REJETE) {
            details.put("motif", motif);
        }
        historiqueActionService.enregistrer(document.getDossier().getId(), TypeActionHistorique.DOCUMENT_PARTAGE_ARBITRE, details);
        return versReponse(document);
    }

    /** La file du DJ et de la DJA : les partages proposés, le plus ancien d'abord. */
    @Transactional(readOnly = true)
    public PageResponse<PartageAvocatResponse> propositions(Pageable pageable) {
        return PageResponse.of(documentRepository.findByPartageAvocatAndEstSupprimeFalseOrderByPartageLeAsc(
                StatutPartageAvocat.PROPOSE, pageable), this::versReponse);
    }

    /** Une pièce supprimée ne se partage plus : elle n'existe plus pour personne hors de l'audit. */
    private Document documentOuErreur(Long documentId) {
        return documentRepository.findById(documentId)
                .filter(document -> !Boolean.TRUE.equals(document.getEstSupprime()))
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Document introuvable : " + documentId));
    }

    private static void reinitialiser(Document document) {
        document.setPartageAvocat(StatutPartageAvocat.NON);
        document.setPartagePar(null);
        document.setPartageLe(null);
        document.setPartageValidePar(null);
        document.setPartageValideLe(null);
    }

    private static boolean estDirection(Utilisateur utilisateur) {
        return utilisateur != null && utilisateur.getProfil() != null
                && DIRECTION.contains(utilisateur.getProfil().getCode());
    }

    private PartageAvocatResponse versReponse(Document document) {
        return new PartageAvocatResponse(document.getId(), document.getDossier().getId(),
                document.getDossier().getReference(), document.getNomFichier(), document.getTypeDocument(),
                document.getPartageAvocat(), nomComplet(document.getPartagePar()), document.getPartageLe());
    }

    private static String nomComplet(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        return Stream.of(utilisateur.getPrenom(), utilisateur.getNom())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }
}
