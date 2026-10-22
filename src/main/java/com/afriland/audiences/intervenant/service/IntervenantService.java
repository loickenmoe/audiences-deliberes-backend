package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.notification.service.Coordonnees;
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
 * Administration of the participant reference data. Lawyers are required for case assignment, fee
 * requests, publications and appointments.
 */
@Service
@RequiredArgsConstructor
public class IntervenantService {

    private final IntervenantRepository intervenantRepository;
    private final AvocatRepository avocatRepository;
    private final HistoriqueActionService historiqueActionService;

    /**
     * RG-INT-01/02: an {@code AVOCAT} must have a unique Keycloak account; an
     * {@code AUTRE_PRESTATAIRE} never has one. Both carry a notification flag (consent to be
     * contacted outside the application), on by default for a lawyer and off for other providers.
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
        if (avocatRepository.findByCompteKeycloak(request.compteKeycloak()).isPresent()) {
            throw ApiException.conflictSurChamp(ErrorCodes.CONFLIT,
                    "Un avocat est déjà rattaché au compte « " + request.compteKeycloak() + " »", "compteKeycloak");
        }

        Avocat avocat = avocatRepository.save(Avocat.builder()
                .nom(request.nom())
                .email(courrielValide(request.email()))
                .telephone(telephoneValide(request.telephone()))
                .compteKeycloak(request.compteKeycloak())
                // On by default: the lawyer has an ongoing relationship with the bank.
                .notification(request.notification() == null || request.notification())
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
                .email(courrielValide(request.email()))
                .telephone(telephoneValide(request.telephone()))
                .notification(request.notification() != null && request.notification())
                .build());
        return toResponse(prestataire);
    }

    /**
     * Updates a participant's descriptive fields (see {@link ModifierIntervenantRequest}). Only
     * actually changed fields are logged, with before/after values; a no-op request writes nothing.
     */
    @Transactional
    public IntervenantResponse modifier(Long id, ModifierIntervenantRequest request) {
        Intervenant intervenant = intervenantRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Intervenant introuvable : " + id));
        String nom = request.nom().trim();
        String email = courrielValide(request.email());
        String telephone = telephoneValide(request.telephone());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("prestataireId", intervenant.getId());
        details.put("intervenant", nom);
        noterSiChange(details, "ancienNom", "nouveauNom", intervenant.getNom(), nom);
        noterSiChange(details, "ancienEmail", "nouvelEmail", intervenant.getEmail(), email);
        noterSiChange(details, "ancienTelephone", "nouveauTelephone", intervenant.getTelephone(), telephone);

        if (intervenant instanceof Avocat avocat) {
            // The Keycloak account is immutable; only the consent flag can change.
            boolean notification = request.notification() == null || request.notification();
            noterSiChange(details, "ancienneNotification", "nouvelleNotification", avocat.getNotification(), notification);
            avocat.setNotification(notification);
        } else if (intervenant instanceof AutrePrestataire autre) {
            boolean notification = request.notification() != null && request.notification();
            noterSiChange(details, "ancienneNotification", "nouvelleNotification", autre.getNotification(), notification);
            autre.setNotification(notification);
        }

        // The first two entries identify the row; any extra entry means a field changed.
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

    /**
     * Validates and normalizes the phone number at entry, so errors surface on the field rather than
     * at send time. An empty value is stored as absent. A number entered as "654 47 67 96" is stored
     * as "237654476796"; anything matching none of the three accepted forms is rejected, not guessed.
     */
    private static String telephoneValide(String saisie) {
        String telephone = videEnNull(saisie);
        if (telephone == null) {
            return null;
        }
        return Coordonnees.telephone(telephone).orElseThrow(() -> ApiException.badRequestSurChamp(
                ErrorCodes.REQUETE_INVALIDE,
                "Numéro de téléphone invalide : attendu 6XXXXXXXX, 237XXXXXXXXX ou +237XXXXXXXXX",
                "telephone"));
    }

    /** Same for email: a domain without a dot would pass standard input validation. */
    private static String courrielValide(String saisie) {
        String courriel = videEnNull(saisie);
        if (courriel == null) {
            return null;
        }
        return Coordonnees.courriel(courriel).orElseThrow(() -> ApiException.badRequestSurChamp(
                ErrorCodes.REQUETE_INVALIDE, "Adresse de courriel invalide", "email"));
    }

    private static String videEnNull(String valeur) {
        return StringUtils.hasText(valeur) ? valeur.trim() : null;
    }

    /**
     * Paginated reference-data list, optionally filtered by type and by name (case-insensitive
     * substring). Serves both the admin table and the search-as-you-type selectors.
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
        Boolean notification = intervenant.accepteNotificationsExternes();
        return new IntervenantResponse(intervenant.getId(), typeDe(intervenant), intervenant.getNom(),
                intervenant.getEmail(), intervenant.getTelephone(), compteKeycloak, notification,
                intervenant.getCreatedAt());
    }
}
