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
 * Exposes the closed reference lists needed by frontend forms. Without them the case file creation
 * screen could not populate its "nature" list, while the backend rejects (400) any nature outside
 * the taxonomy (RG-DOS-02).
 * <p>
 * Read-only: these lists are administered by the DJ in the database (CONF02); the user stories
 * require no write access.
 */
@RestController
@RequestMapping("${api.prefix}/referentiels")
@RequiredArgsConstructor
public class ReferentielController {

    private final NatureDossierRepository natureDossierRepository;
    private final TypeClientSensibleRepository typeClientSensibleRepository;

    /** Case file nature taxonomy, filterable by category (the form adapts to the chosen category). */
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

    /** Active sensitive client types (RG-DOS-09), for the "sensitive case file" drop-down. */
    @GetMapping("/types-client-sensible")
    @PreAuthorize("hasRole('CONSULTATION')")
    public List<TypeClientSensibleResponse> typesClientSensible() {
        return typeClientSensibleRepository.findByActifTrue().stream()
                .map(t -> new TypeClientSensibleResponse(t.getId(), t.getCode(), t.getLibelle()))
                .toList();
    }
}
