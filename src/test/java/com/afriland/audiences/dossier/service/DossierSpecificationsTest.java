package com.afriland.audiences.dossier.service;

import com.afriland.audiences.dossier.domain.Dossier;
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
 * M14 — les filtres optionnels de {@code GET /dossiers} n'étaient couverts par aucun test : seule
 * la composition des prédicats est vérifiable sans base (l'exécution SQL réelle l'est, elle, par
 * les tests bout en bout).
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
        Predicate predicate = DossierSpecifications.withFilters(null, null, null, null, null, null).toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(conjonction);
        verify(cb, never()).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void filtreNatureVide_estIgnore() {
        DossierSpecifications.withFilters("   ", null, null, null, null, null).toPredicate(root, query, cb);

        verify(cb, never()).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void filtreNature_ajouteUneEgaliteInsensibleALaCasse() {
        DossierSpecifications.withFilters("Assignation", null, null, null, null, null).toPredicate(root, query, cb);

        verify(cb).lower(any());
        verify(cb).equal(any(Expression.class), any(Object.class));
        verify(cb, times(1)).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void filtreCategorie_ajouteUneEgaliteStricte() {
        DossierSpecifications.withFilters(null, CategorieDossier.RECOUVREMENT, null, null, null, null).toPredicate(root, query, cb);

        verify(cb, times(1)).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void filtreJuridiction_ajouteUnLike() {
        DossierSpecifications.withFilters(null, null, "Douala", null, null, null).toPredicate(root, query, cb);

        verify(cb).like(any(Expression.class), anyString());
        verify(cb, times(1)).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void troisFiltresCombines_ajoutentTroisPredicats() {
        DossierSpecifications.withFilters("Assignation", CategorieDossier.EXPLOITATION_LITIGES, "Douala", null, null, null)
                .toPredicate(root, query, cb);

        verify(cb, times(3)).and(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void laReferenceEstRechercheeEnPartiel() {
        DossierSpecifications.withFilters(null, null, null, "DOS-2026", null, null)
                .toPredicate(root, query, cb);

        // Une référence se retient rarement en entier : `like` et non `equal`.
        verify(cb).like(any(Expression.class), eq("%dos-2026%"));
    }

    @Test
    void leClientEstFiltreSurSonIdentifiant() {
        when(chemin.get(anyString())).thenReturn(chemin);

        DossierSpecifications.withFilters(null, null, null, null, 42L, null)
                .toPredicate(root, query, cb);

        verify(cb).equal(any(Expression.class), eq(42L));
    }

    @Test
    void unFiltreReferenceVideNAjouteRien() {
        DossierSpecifications.withFilters(null, null, null, "  ", null, null)
                .toPredicate(root, query, cb);

        verify(cb, never()).like(any(Expression.class), anyString());
    }
}
