package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.intervenant.domain.TypeIntervenant;
import com.afriland.audiences.intervenant.web.dto.CreerIntervenantRequest;
import com.afriland.audiences.intervenant.web.dto.IntervenantResponse;
import com.afriland.audiences.referentiel.domain.AutrePrestataire;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Intervenant;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

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

    /** Liste du référentiel — alimente les sélecteurs d'affectation et de constitution du frontend. */
    @Transactional(readOnly = true)
    public List<IntervenantResponse> lister(TypeIntervenant type) {
        return intervenantRepository.findAll().stream()
                .filter(i -> type == null || type == typeDe(i))
                .map(this::toResponse)
                .toList();
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
