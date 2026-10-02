package com.afriland.audiences.common.reference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La numérotation des références, sur un PostgreSQL réel.
 *
 * <p><b>Ce qui ne se teste qu'ici.</b> Le format se vérifie sans base ; l'unicité, non. Toute la
 * valeur de V36 tient dans une promesse — deux créations simultanées n'obtiennent jamais le même
 * numéro — et cette promesse repose sur le comportement de {@code ON CONFLICT DO UPDATE} dans
 * PostgreSQL. Un test à mocks ne pourrait que répéter ce que le code prétend faire.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GenerateurReferenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_reference")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private CompteurReferenceRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void le_premier_numero_de_l_annee_est_un() {
        int premier = repository.prochainNumero("FAMILLE_PREMIER", 2026);

        assertThat(premier).isEqualTo(1);
    }

    @Test
    void les_numeros_se_suivent() {
        List<Integer> servis = IntStream.range(0, 5)
                .mapToObj(i -> repository.prochainNumero("FAMILLE_SUITE", 2026))
                .toList();

        assertThat(servis).containsExactly(1, 2, 3, 4, 5);
    }

    /** Le compteur est annuel : c'est tout l'écart assumé avec la séquence globale de V21. */
    @Test
    void le_numero_repart_a_un_a_chaque_annee() {
        repository.prochainNumero("FAMILLE_ANNEE", 2026);
        repository.prochainNumero("FAMILLE_ANNEE", 2026);

        assertThat(repository.prochainNumero("FAMILLE_ANNEE", 2027)).isEqualTo(1);
        // Et l'année précédente reprend où elle en était, sans avoir été perturbée.
        assertThat(repository.prochainNumero("FAMILLE_ANNEE", 2026)).isEqualTo(3);
    }

    @Test
    void deux_familles_ne_partagent_pas_leur_compteur() {
        repository.prochainNumero("FAMILLE_A", 2026);
        repository.prochainNumero("FAMILLE_A", 2026);

        assertThat(repository.prochainNumero("FAMILLE_B", 2026)).isEqualTo(1);
    }

    /**
     * La promesse de V36, mise à l'épreuve : cinquante demandes concurrentes, cinquante numéros
     * distincts et contigus. C'est le seul test qui distingue un compteur atomique d'un
     * « lire puis écrire » — lequel passerait tous les autres.
     */
    @Test
    void cinquante_demandes_simultanees_donnent_cinquante_numeros_distincts() throws Exception {
        int demandes = 50;
        ExecutorService executeur = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Integer>> travaux = IntStream.range(0, demandes)
                    .<Callable<Integer>>mapToObj(i -> () -> repository.prochainNumero("FAMILLE_CONCURRENTE", 2026))
                    .toList();

            Set<Integer> numeros = executeur.invokeAll(travaux).stream()
                    .map(GenerateurReferenceIntegrationTest::resultat)
                    .collect(Collectors.toSet());

            assertThat(numeros).hasSize(demandes);
            assertThat(numeros).containsAll(IntStream.rangeClosed(1, demandes).boxed().toList());
        } finally {
            executeur.shutdownNow();
        }
    }

    /**
     * La reprise de V36 : un compteur posé à la main sur une référence déjà portée ne resert pas ce
     * numéro. C'est ce qui protège une base où des références du format attribué existeraient déjà.
     */
    @Test
    void un_compteur_repris_ne_resert_pas_un_numero_deja_porte() {
        jdbc.update("INSERT INTO compteur_reference (famille, annee, dernier) VALUES (?, ?, ?)",
                "FAMILLE_REPRISE", 2026, 7);

        assertThat(repository.prochainNumero("FAMILLE_REPRISE", 2026)).isEqualTo(8);
    }

    /** Le générateur met en forme ce que la base lui donne, sur l'année en cours. */
    @Test
    void le_generateur_rend_une_reference_complete() {
        GenerateurReference generateur = new GenerateurReference(repository);

        String reference = generateur.prochaine(FamilleReference.DOSSIER);

        assertThat(reference).matches("DOS-" + LocalDate.now().getYear() + "-\\d{4,}");
    }

    private static Integer resultat(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception echec) {
            throw new IllegalStateException("une demande de numéro a échoué", echec);
        }
    }
}
