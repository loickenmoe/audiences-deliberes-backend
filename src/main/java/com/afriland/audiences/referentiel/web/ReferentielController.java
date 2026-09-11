package com.afriland.audiences.referentiel.web;

import com.afriland.audiences.referentiel.domain.CategorieDossier;
import com.afriland.audiences.referentiel.repository.NatureDossierRepository;
import com.afriland.audiences.referentiel.repository.TypeClientSensibleRepository;
import com.afriland.audiences.referentiel.web.dto.NatureDossierResponse;
import com.afriland.audiences.referentiel.web.dto.TypeClientSensibleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M15 — expose les référentiels fermés nécessaires aux formulaires du frontend. Sans eux, l'écran
 * « Création de dossier » ne peut pas peupler sa liste « nature » alors que le backend rejette
 * (400) toute nature hors taxonomie (RG-DOS-02) — cf. Q-68.
 * <p>
 * Lecture seule : ces référentiels sont administrés par le DJ via la base (CONF02), aucune écriture
 * n'est demandée par les user stories.
 */
@RestController
@RequestMapping("${api.prefix}/referentiels")
@RequiredArgsConstructor
public class ReferentielController {

    private final NatureDossierRepository natureDossierRepository;
    private final TypeClientSensibleRepository typeClientSensibleRepository;

    /** Taxonomie des natures de dossier, filtrable par catégorie (le formulaire s'adapte à la catégorie choisie). */
    @GetMapping("/natures-dossier")
    @PreAuthorize("hasRole('CONSULTATION')")
    public List<NatureDossierResponse> naturesDossier(@RequestParam(required = false) CategorieDossier categorie) {
        var natures = categorie == null
                ? natureDossierRepository.findByActifTrue()
                : natureDossierRepository.findByCategorieAndActifTrue(categorie);
        return natures.stream()
                .map(n -> new NatureDossierResponse(n.getId(), n.getCode(), n.getLibelle(), n.getCategorie()))
                .toList();
    }

    /** Types de client sensible actifs (RG-DOS-09) — liste déroulante du volet « dossier sensible ». */
    @GetMapping("/types-client-sensible")
    @PreAuthorize("hasRole('CONSULTATION')")
    public List<TypeClientSensibleResponse> typesClientSensible() {
        return typeClientSensibleRepository.findByActifTrue().stream()
                .map(t -> new TypeClientSensibleResponse(t.getId(), t.getCode(), t.getLibelle()))
                .toList();
    }
}
