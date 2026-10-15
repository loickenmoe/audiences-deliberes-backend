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
 * Le référentiel des agences (métier, 2026-10-03, observation 2).
 *
 * <p>Lu par tous ceux qui ouvrent un dossier, tenu par le DJ et la DJA. La liste déroulante du
 * formulaire s'y alimente, et la saisie libre a disparu : un code d'agence ne s'invente plus.
 *
 * <p><b>On désactive, on ne supprime pas.</b> Un dossier ouvert sous une agence fermée reste un
 * dossier valide, et son code doit continuer de désigner quelque chose. La suppression effacerait
 * la réponse à une question que l'audit pose encore des années après.
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
     * Le code <b>ne se modifie pas</b>.
     *
     * <p>Les dossiers le portent en clair, et le changer les détacherait silencieusement de leur
     * agence. Une agence qui change de code est une autre agence : on la crée, et on désactive
     * l'ancienne.
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

    /** Retire l'agence des listes de choix, sans toucher aux dossiers qui la citent. */
    @Transactional
    public AgenceResponse desactiver(Long id) {
        Agence agence = agenceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Agence introuvable : " + id));
        agence.setActif(false);
        return toResponse(agenceRepository.save(agence));
    }

    /**
     * Rouvre une agence fermée (métier, 2026-10-06).
     *
     * <p>Symétrique de {@link #desactiver(Long)}, et pour deux raisons. Une fermeture peut être une
     * <b>erreur de manipulation</b> : sans réouverture, la seule issue était de recréer l'agence sous
     * un autre code, c'est-à-dire d'inventer un code - exactement ce que le référentiel empêche.
     * Et une agence <b>réouvre</b> : locaux rendus, travaux terminés. C'est la même agence, elle
     * garde son code, et les dossiers ouverts sous elle n'ont jamais cessé de le désigner.
     *
     * <p>Réouvrir une agence déjà ouverte n'est pas une erreur : l'état demandé est atteint, et
     * refuser obligerait l'appelant à lire avant d'écrire pour rien.
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
     * Ce code désigne-t-il une agence ouverte ?
     *
     * <p>Appelé à la création d'un dossier. On exige {@code actif} : proposer une agence fermée
     * n'aurait pas de sens, et l'accepter par l'API ferait diverger l'écran et le serveur.
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
     * Le motif de recherche, échappé.
     *
     * <p>Même procédé que la recherche jurisprudentielle : sans échappement, un pourcent saisi par
     * l'utilisateur ramènerait tout le référentiel, et un souligné n'importe quel caractère. Le
     * caractère d'échappement est le point d'exclamation, déclaré dans la requête.
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
