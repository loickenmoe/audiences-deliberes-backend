package com.afriland.audiences.referentiel.service;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.referentiel.domain.Agence;
import com.afriland.audiences.referentiel.repository.AgenceRepository;
import com.afriland.audiences.referentiel.web.dto.AgenceRequest;
import com.afriland.audiences.referentiel.web.dto.AgenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Branch reference list.
 *
 * <p>Read by everyone who opens a case file, maintained by the DJ and DJA. It feeds the form's
 * drop-down list, so a branch code can no longer be made up.
 *
 * <p><b>Deactivate, never delete.</b> A case file opened under a closed branch remains valid, and
 * its code must keep designating something; deletion would erase the answer to an audit question
 * asked years later.
 */
@Service
@RequiredArgsConstructor
public class AgenceService {

    private final AgenceRepository agenceRepository;

    @Transactional(readOnly = true)
    public PageResponse<AgenceResponse> lister(String recherche, boolean actifSeulement,
                                               Pageable pageable) {
        return PageResponse.of(
                agenceRepository.rechercher(motif(recherche), actifSeulement, pageable),
                AgenceService::toResponse);
    }

    @Transactional
    public AgenceResponse creer(AgenceRequest request) {
        String code = request.code().trim();
        if (agenceRepository.existsByCode(code)) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "Une agence porte déjà le code " + code);
        }
        Agence agence = Agence.builder()
                .code(code)
                .nom(request.nom().trim())
                .ville(vide(request.ville()))
                .adresse(vide(request.adresse()))
                .telephone(vide(request.telephone()))
                .actif(request.actif() == null || request.actif())
                .build();
        return toResponse(agenceRepository.save(agence));
    }

    /**
     * The code <b>cannot be changed</b>.
     *
     * <p>Case files carry it in clear, and changing it would silently detach them from their branch.
     * A branch that changes code is a different branch: create it and deactivate the old one.
     */
    @Transactional
    public AgenceResponse modifier(Long id, AgenceRequest request) {
        Agence agence = agenceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Agence introuvable : " + id));
        if (!agence.getCode().equals(request.code().trim())) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Le code d'une agence ne se modifie pas : les dossiers le portent en clair. "
                            + "Créez la nouvelle agence et désactivez celle-ci.");
        }
        agence.setNom(request.nom().trim());
        agence.setVille(vide(request.ville()));
        agence.setAdresse(vide(request.adresse()));
        agence.setTelephone(vide(request.telephone()));
        if (request.actif() != null) {
            agence.setActif(request.actif());
        }
        return toResponse(agenceRepository.save(agence));
    }

    /** Removes the branch from selection lists, without touching the case files that cite it. */
    @Transactional
    public AgenceResponse desactiver(Long id) {
        Agence agence = agenceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Agence introuvable : " + id));
        agence.setActif(false);
        return toResponse(agenceRepository.save(agence));
    }

    /**
     * Reopens a closed branch.
     *
     * <p>Counterpart of {@link #desactiver(Long)}: a closure may be a <b>handling error</b> (otherwise
     * the only way out would be recreating the branch under a made-up code), and a branch may genuinely
     * <b>reopen</b>, keeping its code.
     *
     * <p>Reopening an already open branch is not an error: the requested state is reached.
     */
    @Transactional
    public AgenceResponse reactiver(Long id) {
        Agence agence = agenceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Agence introuvable : " + id));
        agence.setActif(true);
        return toResponse(agenceRepository.save(agence));
    }

    /**
     * Does this code designate an open branch?
     *
     * <p>Called when creating a case file. {@code actif} is required: offering a closed branch makes no
     * sense, and accepting it through the API would make the UI and the server diverge.
     */
    @Transactional(readOnly = true)
    public boolean existeEtEstActive(String code) {
        return code != null && agenceRepository.existsByCodeAndActifTrue(code.trim());
    }

    private static String vide(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        return valeur.trim();
    }

    /**
     * The search pattern, escaped.
     *
     * <p>Without escaping, a percent typed by the user would return the whole list and an underscore
     * would match any character. The escape character is the exclamation mark, declared in the query.
     */
    static String motif(String recherche) {
        if (recherche == null || recherche.isBlank()) {
            return null;
        }
        String echappe = recherche.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + echappe + "%";
    }

    private static AgenceResponse toResponse(Agence agence) {
        return new AgenceResponse(agence.getId(), agence.getCode(), agence.getNom(),
                agence.getVille(), agence.getAdresse(), agence.getTelephone(), agence.getActif());
    }
}
