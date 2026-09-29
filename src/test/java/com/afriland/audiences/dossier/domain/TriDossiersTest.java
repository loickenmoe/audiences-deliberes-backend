package com.afriland.audiences.dossier.domain;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le tri de {@code GET /dossiers}. Deux propriétés comptent et ne se voient pas à la lecture :
 * chaque tri se termine par l'identifiant — sans quoi la pagination peut répéter ou sauter une
 * ligne — et aucun ne nomme une propriété absente de l'entité, ce qui produirait une erreur 500 au
 * premier clic.
 */
class TriDossiersTest {

    @Test
    void chaque_tri_se_termine_par_l_identifiant() {
        for (TriDossiers tri : TriDossiers.values()) {
            var proprietes = tri.sort().stream().map(Sort.Order::getProperty).toList();
            assertThat(proprietes).as("tri %s", tri).endsWith("id");
        }
    }

    @Test
    void chaque_tri_ne_nomme_que_des_champs_de_l_entite() {
        var champs = java.util.Arrays.stream(Dossier.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toList();
        for (TriDossiers tri : TriDossiers.values()) {
            for (Sort.Order ordre : tri.sort()) {
                assertThat(champs).as("tri %s", tri).contains(ordre.getProperty());
            }
        }
    }

    @Test
    void le_defaut_reste_le_plus_recent_d_abord() {
        var premier = TriDossiers.RECENT.sort().iterator().next();

        assertThat(premier.getProperty()).isEqualTo("createdAt");
        assertThat(premier.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void le_risque_le_plus_eleve_vient_en_premier() {
        var premier = TriDossiers.RISQUE.sort().iterator().next();

        assertThat(premier.getProperty()).isEqualTo("risqueEncouru");
        assertThat(premier.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void la_reference_est_triee_dans_l_ordre_alphabetique() {
        var premier = TriDossiers.REFERENCE.sort().iterator().next();

        assertThat(premier.getProperty()).isEqualTo("reference");
        assertThat(premier.getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
