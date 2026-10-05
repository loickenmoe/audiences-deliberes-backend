package com.afriland.audiences.publication.repository;

import com.afriland.audiences.publication.domain.CompteRenduAudience;
import com.afriland.audiences.publication.domain.StatutPublication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Les comptes rendus déposés par les avocats, lus par audience.
 *
 * <p>Distincts du compte rendu du juriste, qui vit sur l'audience elle-même : une audience peut
 * donc en porter deux, un seul, ou aucun. Le journal des audiences (observation métier du
 * 2026-09-25) les montre côte à côte, car c'est leur écart qui renseigne sur les responsabilités.
 */
public interface CompteRenduAudienceRepository extends JpaRepository<CompteRenduAudience, Long> {

    /**
     * Les comptes rendus de ces audiences, dans l'état de validation demandé.
     *
     * <p>Chargés par lot pour une page entière d'audiences, jamais un par ligne : le journal d'une
     * journée chargée en compte plusieurs dizaines.
     */
    List<CompteRenduAudience> findByAudienceIdInAndStatutValidation(Collection<Long> audienceIds,
                                                                    StatutPublication statutValidation);

    /**
     * RG-INT-11 : l'avocat a-t-il rendu compte de cette audience ?
     *
     * <p>Un compte rendu REJETE ne compte pas : l'Assistante l'a écarté, l'audience reste sans
     * récit, et l'avocat en doit toujours un. La relance reprend donc après un rejet — ce qui
     * est le comportement voulu, l'avocat ayant par ailleurs été averti du rejet.
     */
    boolean existsByAudienceIdAndStatutValidationIn(Long audienceId,
                                                    Collection<StatutPublication> statuts);
}
