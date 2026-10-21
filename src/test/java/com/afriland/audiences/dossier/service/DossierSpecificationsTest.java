package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.StatutValidation;
import com.afriland.audiences.referentiel.domain.CategorieDossier;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Optional filters of {@code GET /dossiers}: only predicate composition is checked here, without a
 * database (actual SQL execution is covered by end-to-end tests).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DossierSpecificationsTest {

    @Mock private Root<Dossier> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;
    @Mock private Predicate conjonction;
    @Mock private Predicate combinaison;
    @Mock private Path<Object> chemin;
    @Mock private Expression<String> expression;

    @BeforeEach
    void setUp() {
        when(cb.conjunction()).thenReturn(conjonction);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(combinaison);
        when(root.get(anyString())).thenReturn(chemin);
        when(cb.lower(any())).thenReturn(expression);
        when(cb.equal(any(Expression.class), any(Object.class))).thenReturn(combinaison);
        when(cb.like(any(Expression.class), anyString())).thenReturn(combinaison);
    }

    @Test
    void sansAucunFiltre_laConjonctionResteNeutre() {
        Predicate predicate = DossierSpecifications.withFilters(new FiltresDossiers(null, null, null, null, null, null, null, null, null, null, null), null).toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(conjonction);
        verify(cb, never()).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void filtreNatureVide_estIgnore() {
        DossierSpecifications.withFilters(new FiltresDossiers("   ", null, null, null, null, null, null, null, null, null, null), null).toPredicate(root, query, cb);

        verify(cb, never()).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void filtreNature_ajouteUneEgaliteInsensibleALaCasse() {
        DossierSpecifications.withFilters(new FiltresDossiers("Assignation", null, null, null, null, null, null, null, null, null, null), null).toPredicate(root, query, cb);

        verify(cb).lower(any());
        verify(cb).equal(any(Expression.class), any(Object.class));
        verify(cb, times(1)).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void filtreCategorie_ajouteUneEgaliteStricte() {
        DossierSpecifications.withFilters(new FiltresDossiers(null, CategorieDossier.RECOUVREMENT, null, null, null, null, null, null, null, null, null), null).toPredicate(root, query, cb);

        verify(cb, times(1)).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void filtreJuridiction_ajouteUnLike() {
        DossierSpecifications.withFilters(new FiltresDossiers(null, null, "Douala", null, null, null, null, null, null, null, null), null).toPredicate(root, query, cb);

        verify(cb).like(any(Expression.class), anyString());
        verify(cb, times(1)).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void troisFiltresCombines_ajoutentTroisPredicats() {
        DossierSpecifications.withFilters(new FiltresDossiers("Assignation", CategorieDossier.EXPLOITATION_LITIGES, "Douala", null, null, null, null, null, null, null, null), null)
                .toPredicate(root, query, cb);

        verify(cb, times(3)).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void laReferenceEstRechercheeEnPartiel() {
        DossierSpecifications.withFilters(new FiltresDossiers(null, null, null, "DOS-2026", null, null, null, null, null, null, null), null)
                .toPredicate(root, query, cb);

        // A reference is rarely remembered in full: `like`, not `equal`.
        verify(cb).like(any(Expression.class), eq("%dos-2026%"));
    }

    @Test
    void leClientEstFiltreSurSonIdentifiant() {
        when(chemin.get(anyString())).thenReturn(chemin);

        DossierSpecifications.withFilters(new FiltresDossiers(null, null, null, null, 42L, null, null, null, null, null, null), null)
                .toPredicate(root, query, cb);

        verify(cb).equal(any(Expression.class), eq(42L));
    }

    /**
     * `estSensible` is a boolean: the test mainly checks it is **not** confused with "not set", otherwise
     * filtering on non-sensitive cases would return everything.
     */
    @Test
    void laSensibiliteEstFiltreeSurSaValeurBooleenne() {
        DossierSpecifications.withFilters(new FiltresDossiers(null, null, null, null, null, Boolean.TRUE, null, null, null, null, null), null)
                .toPredicate(root, query, cb);

        verify(cb).equal(any(Expression.class), eq(Boolean.TRUE));
    }

    @Test
    void unFiltreSensibiliteFauxNEstPasConfonduAvecAbsent() {
        DossierSpecifications.withFilters(new FiltresDossiers(null, null, null, null, null, Boolean.FALSE, null, null, null, null, null), null)
                .toPredicate(root, query, cb);

        verify(cb).equal(any(Expression.class), eq(Boolean.FALSE));
    }

    /** `EN_ATTENTE` is the queue of sensitivity flags awaiting DJ/DJA validation. */
    @Test
    void leStatutDeSensibiliteEstFiltre() {
        DossierSpecifications.withFilters(new FiltresDossiers(null, null, null, null, null, null, StatutValidation.EN_ATTENTE, null, null, null, null), null)
                .toPredicate(root, query, cb);

        verify(cb).equal(any(Expression.class), eq(StatutValidation.EN_ATTENTE));
    }

    @Test
    void aucunFiltreDeSensibiliteNAjouteRien() {
        DossierSpecifications.withFilters(new FiltresDossiers(null, null, null, null, null, null, null, null, null, null, null), null)
                .toPredicate(root, query, cb);

        verify(cb, never()).equal(any(Expression.class), any(Boolean.class));
    }

    @Test
    void unFiltreReferenceVideNAjouteRien() {
        DossierSpecifications.withFilters(new FiltresDossiers(null, null, null, "  ", null, null, null, null, null, null, null), null)
                .toPredicate(root, query, cb);

        verify(cb, never()).like(any(Expression.class), anyString());
    }
}
