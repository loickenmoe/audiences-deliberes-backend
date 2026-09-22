package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.intervenant.domain.TypeIntervenant;
import com.afriland.audiences.intervenant.web.dto.CreerIntervenantRequest;
import com.afriland.audiences.intervenant.web.dto.IntervenantResponse;
import com.afriland.audiences.intervenant.web.dto.ModifierIntervenantRequest;
import com.afriland.audiences.referentiel.domain.AutrePrestataire;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Intervenant;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;



/**
 * M15 — administration du référentiel des intervenants (Q-68). Aucun endpoint de création
 * n'existait : les avocats devaient être insérés en SQL, alors qu'ils conditionnent l'affectation
 * d'un dossier, les demandes de frais, les publications et les constitutions.
 */
@Service
@RequiredArgsConstructor
public class IntervenantService {

    private final IntervenantRepository intervenantRepository;
    private final AvocatRepository avocatRepository;
    private final HistoriqueActionService historiqueActionService;

    /**
     * RG-INT-01/02 : un {@code AVOCAT} porte obligatoirement un compte Keycloak (unique) ; un
     * {@code AUTRE_PRESTATAIRE} n'en a jamais et porte à la place un indicateur de notification.
     */
    @Transactional
    public IntervenantResponse creer(CreerIntervenantRequest request) {
        if (request.type() == TypeIntervenant.AVOCAT) {
            return creerAvocat(request);
        }
        return creerAutrePrestataire(request);
    }

    private IntervenantResponse creerAvocat(CreerIntervenantRequest request) {
        if (!StringUtils.hasText(request.compteKeycloak())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Un avocat doit porter un compte Keycloak (RG-INT-01)");
        }
        if (request.notification() != null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Le champ notification ne s'applique qu'à un autre prestataire (RG-INT-02)");
        }
        if (avocatRepository.findByCompteKeycloak(request.compteKeycloak()).isPresent()) {
            throw ApiException.conflictSurChamp(ErrorCodes.CONFLIT,
                    "Un avocat est déjà rattaché au compte « " + request.compteKeycloak() + " »", "compteKeycloak");
        }

        Avocat avocat = avocatRepository.save(Avocat.builder()
                .nom(request.nom())
                .email(request.email())
                .telephone(request.telephone())
                .compteKeycloak(request.compteKeycloak())
                .build());
        return toResponse(avocat);
    }

    private IntervenantResponse creerAutrePrestataire(CreerIntervenantRequest request) {
        if (StringUtils.hasText(request.compteKeycloak())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Un autre prestataire ne dispose d'aucun compte applicatif (RG-INT-02)");
        }

        Intervenant prestataire = intervenantRepository.save(AutrePrestataire.builder()
                .nom(request.nom())
                .email(request.email())
                .telephone(request.telephone())
                .notification(request.notification() != null && request.notification())
                .build());
        return toResponse(prestataire);
    }

    /**
     * Modifie un intervenant (Q-117). Le type et l'identifiant Keycloak d'un avocat sont figés ;
     * seules les informations descriptives se corrigent — cf. {@link ModifierIntervenantRequest}.
     *
     * <p>Comme pour un client (Q-116), seuls les champs <b>réellement changés</b> sont historisés, avec
     * leur valeur d'avant et d'après, et une requête qui ne change rien n'écrit rien.
     */
    @Transactional
    public IntervenantResponse modifier(Long id, ModifierIntervenantRequest request) {
        Intervenant intervenant = intervenantRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Intervenant introuvable : " + id));
        String nom = request.nom().trim();
        String email = videEnNull(request.email());
        String telephone = videEnNull(request.telephone());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("prestataireId", intervenant.getId());
        details.put("intervenant", nom);
        noterSiChange(details, "ancienNom", "nouveauNom", intervenant.getNom(), nom);
        noterSiChange(details, "ancienEmail", "nouvelEmail", intervenant.getEmail(), email);
        noterSiChange(details, "ancienTelephone", "nouveauTelephone", intervenant.getTelephone(), telephone);

        if (intervenant instanceof Avocat) {
            // Son compte Keycloak ne bouge pas : il n'est pas dans la requête.
            if (request.notification() != null) {
                throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                        "Le champ notification ne s'applique qu'à un autre prestataire (RG-INT-02)");
            }
        } else if (intervenant instanceof AutrePrestataire autre) {
            boolean notification = request.notification() != null && request.notification();
            noterSiChange(details, "ancienneNotification", "nouvelleNotification", autre.getNotification(), notification);
            autre.setNotification(notification);
        }

        // prestataireId et intervenant identifient la ligne ; au-delà, c'est qu'un champ a changé.
        if (details.size() > 2) {
            intervenant.setNom(nom);
            intervenant.setEmail(email);
            intervenant.setTelephone(telephone);
            intervenant = intervenantRepository.save(intervenant);
            historiqueActionService.enregistrer(null, TypeActionHistorique.INTERVENANT_MODIFIE, details);
        }
        return toResponse(intervenant);
    }

    private static void noterSiChange(Map<String, Object> details, String cleAvant, String cleApres,
                                      Object avant, Object apres) {
        if (!Objects.equals(avant, apres)) {
            details.put(cleAvant, avant);
            details.put(cleApres, apres);
        }
    }

    /** Un champ facultatif vidé à l'écran arrive en chaîne vide : il est enregistré comme absent. */
    private static String videEnNull(String valeur) {
        return StringUtils.hasText(valeur) ? valeur.trim() : null;
    }

    /**
     * Liste du référentiel, paginée depuis Q-100.
     *
     * <p>⚠ Cet endpoint sert **deux usages** : l'écran 42, qui est un tableau et veut dix lignes, et
     * trois **sélecteurs** (affectation d'un dossier, constitution d'un prestataire) qui ont besoin
     * de l'annuaire entier. Ces derniers demandent donc explicitement une grande page, comme
     * {@code /avocat/mes-dossiers} le fait déjà. La réponse durable pour un annuaire qui grandirait
     * n'est pas une page plus grande mais une **recherche au fil de la frappe** ; c'est consigné,
     * pas implémenté.
     */
    @Transactional(readOnly = true)
    public PageResponse<IntervenantResponse> lister(TypeIntervenant type, String nom, Pageable pageable) {
        Boolean avocatsSeuls = type == null ? null : type == TypeIntervenant.AVOCAT;
        return PageResponse.of(
                intervenantRepository.rechercher(avocatsSeuls,
                        StringUtils.hasText(nom) ? "%" + nom.toLowerCase() + "%" : null, pageable),
                this::toResponse);
    }

    private TypeIntervenant typeDe(Intervenant intervenant) {
        return intervenant instanceof Avocat ? TypeIntervenant.AVOCAT : TypeIntervenant.AUTRE_PRESTATAIRE;
    }

    private IntervenantResponse toResponse(Intervenant intervenant) {
        String compteKeycloak = intervenant instanceof Avocat avocat ? avocat.getCompteKeycloak() : null;
        Boolean notification = intervenant instanceof AutrePrestataire autre ? autre.getNotification() : null;
        return new IntervenantResponse(intervenant.getId(), typeDe(intervenant), intervenant.getNom(),
                intervenant.getEmail(), intervenant.getTelephone(), compteKeycloak, notification,
                intervenant.getCreatedAt());
    }
}
