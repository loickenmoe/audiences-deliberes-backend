package com.afriland.audiences.integration;

import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.ProfilRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M15 (Q-68) — tests d'intégration des endpoints ajoutés pour rendre le backend consommable par un
 * frontend : référentiels de formulaire, administration des intervenants et listes de travail.
 * <p>
 * Ces endpoints n'ont presque pas de règle métier ; leur risque est ailleurs — routage, filtres de
 * requête et surtout <b>habilitations</b>, qu'un test de service mocké ne peut pas vérifier. D'où
 * un test de bout en bout (contexte Spring complet, PostgreSQL réel migré par Flyway) plutôt que
 * des tests unitaires supplémentaires. Mêmes substitutions assumées qu'en M14 (Q-65) : JWT simulé,
 * {@link MinioClient} mocké.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HabilitationFrontendIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_m15")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final AtomicInteger COMPTEUR = new AtomicInteger();

    @MockBean
    private MinioClient minioClient;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ProfilRepository profilRepository;
    @Autowired private AvocatRepository avocatRepository;

    private Long avocatId;

    @BeforeEach
    void setUp() {
        provisionner("kc-juriste-m15", "JURISTE");
        provisionner("kc-dj-m15", "DJ");
        provisionner("kc-sh-m15", "SH");
        provisionner("kc-assistante-m15", "ASSISTANTE");
        avocatId = avocatRepository.findByCompteKeycloak("kc-avocat-m15")
                .map(Avocat::getId)
                .orElseGet(() -> avocatRepository.save(Avocat.builder()
                        .nom("Me Test M15").email("avocat-m15@test.cm").compteKeycloak("kc-avocat-m15")
                        .build()).getId());
    }

    // ---------------------------------------------------------------- Référentiels de formulaire

    @Test
    @DisplayName("Les natures de dossier sont exposées et filtrables par catégorie")
    void naturesDossier_sontExposeesEtFiltrables() throws Exception {
        JsonNode toutes = lireJson(mockMvc.perform(get(api("/referentiels/natures-dossier"))
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(toutes.size()).isPositive();

        JsonNode recouvrement = lireJson(mockMvc.perform(get(api("/referentiels/natures-dossier"))
                        .param("categorie", "RECOUVREMENT")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(recouvrement.size()).isPositive();
        recouvrement.forEach(n -> assertThat(n.get("categorie").asText()).isEqualTo("RECOUVREMENT"));
        assertThat(recouvrement.size()).isLessThanOrEqualTo(toutes.size());
    }

    @Test
    @DisplayName("Les types de client sensible sont exposés (RG-DOS-09)")
    void typesClientSensible_sontExposes() throws Exception {
        mockMvc.perform(get(api("/referentiels/types-client-sensible"))
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("Les référentiels exigent le rôle CONSULTATION")
    void referentiels_sansRoleConsultation_sontRefuses() throws Exception {
        mockMvc.perform(get(api("/referentiels/natures-dossier"))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- Répertoire des utilisateurs internes

    /**
     * Q-69 — le test le plus important du lot : il rejoue la chaîne complète qui était rompue.
     * Le frontend liste les juristes, en retient un identifiant, et s'en sert pour créer un dossier.
     * Sans {@code GET /utilisateurs}, ce dernier appel était impossible à construire.
     */
    @Test
    @DisplayName("Un juriste listé peut être affecté à un dossier — chaîne complète de création")
    void listerLesJuristes_puisCreerUnDossierAvecLIdentifiantObtenu() throws Exception {
        JsonNode juristes = lireJson(mockMvc.perform(get(api("/utilisateurs"))
                        .param("profil", "JURISTE")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(juristes.size()).isPositive();
        juristes.forEach(u -> {
            assertThat(u.get("profil").asText()).isEqualTo("JURISTE");
            assertThat(u.get("actif").asBoolean()).isTrue();
            assertThat(u.has("keycloakId")).as("l'identifiant Keycloak ne doit pas fuiter").isFalse();
        });

        long juristeId = juristes.get(0).get("id").asLong();
        int n = COMPTEUR.incrementAndGet();
        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client Q69 " + n, "reference", "CLI-Q69-" + n)))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("reference", "DOS-Q69-" + n),
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("categorie", "RECOUVREMENT"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 500_000),
                                Map.entry("dossierCreditOrigine", "CRD-Q69-" + n),
                                Map.entry("pvTransfert", "PV-Q69-" + n),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)))))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.juristesAffectes[0]").value(juristeId));
    }

    @Test
    @DisplayName("Le filtre par profil discrimine, et un avocat ne peut pas énumérer le personnel interne")
    void repertoireDesUtilisateurs_filtreEtHabilitation() throws Exception {
        JsonNode dj = lireJson(mockMvc.perform(get(api("/utilisateurs")).param("profil", "DJ")
                        .with(role("kc-dj-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());
        dj.forEach(u -> assertThat(u.get("profil").asText()).isEqualTo("DJ"));

        JsonNode tous = lireJson(mockMvc.perform(get(api("/utilisateurs"))
                        .with(role("kc-dj-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(tous.size()).isGreaterThan(dj.size());

        // Le profil AVOCAT ne porte pas ROLE_CONSULTATION (cf. audiences-realm.json).
        mockMvc.perform(get(api("/utilisateurs")).with(role("kc-avocat-externe", "ROLE_AVOCAT")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- Administration des intervenants

    @Test
    @DisplayName("Le DJ crée un avocat, puis le retrouve dans le référentiel filtré")
    void creerAvocat_puisLister() throws Exception {
        String compte = "kc-avocat-m15-" + COMPTEUR.incrementAndGet();

        long id = extraireId(mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", "Me Ndjock",
                                "email", compte + "@cabinet.cm", "compteKeycloak", compte)))
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("AVOCAT"))
                .andExpect(jsonPath("$.compteKeycloak").value(compte))
                .andReturn());

        JsonNode avocats = lireJson(mockMvc.perform(get(api("/intervenants"))
                        .param("type", "AVOCAT")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(avocats.findValues("id")).anySatisfy(n -> assertThat(n.asLong()).isEqualTo(id));
        avocats.forEach(a -> assertThat(a.get("type").asText()).isEqualTo("AVOCAT"));
    }

    @Test
    @DisplayName("Un second avocat sur le même compte Keycloak est refusé (409)")
    void creerAvocat_compteDejaUtilise_retourne409() throws Exception {
        String compte = "kc-avocat-m15-" + COMPTEUR.incrementAndGet();
        String corps = json(Map.of("type", "AVOCAT", "nom", "Me Premier", "compteKeycloak", compte));

        mockMvc.perform(post(api("/intervenants")).contentType(MediaType.APPLICATION_JSON).content(corps)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(api("/intervenants")).contentType(MediaType.APPLICATION_JSON).content(corps)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Un autre prestataire est créé sans compte applicatif (RG-INT-02)")
    void creerAutrePrestataire_sansCompte() throws Exception {
        mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AUTRE_PRESTATAIRE", "nom",
                                "Huissier " + COMPTEUR.incrementAndGet(), "notification", true)))
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compteKeycloak").doesNotExist())
                .andExpect(jsonPath("$.notification").value(true));
    }

    @Test
    @DisplayName("Un avocat sans compte Keycloak est refusé (400, RG-INT-01)")
    void creerAvocat_sansCompte_retourne400() throws Exception {
        mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", "Me Sans Compte")))
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("La création d'un intervenant est réservée au DJ/DJA")
    void creerIntervenant_parUnJuriste_estRefuse() throws Exception {
        mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AUTRE_PRESTATAIRE", "nom", "Notaire")))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_CONSULTATION")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- Listes de travail

    @Test
    @DisplayName("Chaque file de travail est servie à son profil et refusée aux autres")
    void filesDeTravail_respectentLesHabilitations() throws Exception {
        // File des publications à valider — Assistante
        mockMvc.perform(get(api("/publications")).param("statut", "DEPOSE")
                        .with(role("kc-assistante-m15", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get(api("/publications")).with(role("kc-sh-m15", "ROLE_SH")))
                .andExpect(status().isForbidden());

        // File des demandes de suppression — DJ/DJA
        mockMvc.perform(get(api("/ged/demandes-suppression")).param("statut", "EN_ATTENTE")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get(api("/ged/demandes-suppression"))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE")))
                .andExpect(status().isForbidden());

        // File des constitutions à signer — SH
        mockMvc.perform(get(api("/constitutions/prestataires")).param("statut", "EN_ATTENTE")
                        .with(role("kc-sh-m15", "ROLE_SH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get(api("/constitutions/prestataires"))
                        .with(role("kc-assistante-m15", "ROLE_ASSISTANTE")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- Sous-ressources d'un dossier

    @Test
    @DisplayName("Les onglets audiences / délibérés / alarmes d'un dossier sont servis, 404 si le dossier n'existe pas")
    void sousRessourcesDossier() throws Exception {
        long dossierId = creerDossier();

        for (String sousRessource : List.of("audiences", "deliberes", "alarmes")) {
            mockMvc.perform(get(api("/dossiers/" + dossierId + "/" + sousRessource))
                            .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

            mockMvc.perform(get(api("/dossiers/999999/" + sousRessource))
                            .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------- Provisionnement en lecture seule (R-06)

    /**
     * Reproduit le scénario exact de R-06 par HTTP : {@code GET /alertes/mes-notifications} est
     * servi par un service {@code @Transactional(readOnly = true)} et résout l'utilisateur courant.
     * Avant M15, la toute première requête d'un nouvel utilisateur — celle que le frontend émet au
     * chargement — retombait en 500 (« cannot execute INSERT in a read-only transaction »).
     */
    @Test
    @DisplayName("R-06 corrigé : un utilisateur inconnu est provisionné depuis un endpoint en lecture seule")
    void utilisateurInconnu_estProvisionneSurUnEndpointDeLecture() throws Exception {
        String nouveau = "kc-nouveau-m15-" + COMPTEUR.incrementAndGet();
        assertThat(utilisateurRepository.findByKeycloakId(nouveau)).isEmpty();

        mockMvc.perform(get(api("/alertes/mes-notifications")).with(role(nouveau, "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // Le profil dérivé du jeton est vérifié par ProvisionnementLectureSeuleIntegrationTest ;
        // ici, seule compte la présence de la ligne — la preuve que l'INSERT a bien eu lieu.
        assertThat(utilisateurRepository.findByKeycloakId(nouveau)).isPresent();
    }

    // ---------------------------------------------------------------- Helpers

    private long creerDossier() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client M15 " + n, "reference", "CLI-M15-" + n)))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());

        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m15").orElseThrow().getId();

        return extraireId(mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("reference", "DOS-M15-" + n),
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("categorie", "RECOUVREMENT"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-M15-" + n),
                                Map.entry("pvTransfert", "PV-M15-" + n),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)))))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void provisionner(String keycloakId, String profilCode) {
        if (utilisateurRepository.findByKeycloakId(keycloakId).isPresent()) {
            return;
        }
        utilisateurRepository.save(Utilisateur.builder()
                .keycloakId(keycloakId)
                .nom(profilCode)
                .prenom("Test")
                .email(keycloakId + "@test.cm")
                .profil(profilRepository.findByCode(profilCode).orElseThrow())
                .actif(true)
                .build());
    }

    /** Cf. {@code ParcoursEndToEndIntegrationTest#role} : les autorités sont fournies explicitement. */
    private RequestPostProcessor role(String keycloakId, String... roles) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .subject(keycloakId)
                        .claim("roles", List.of(roles))
                        .claim("given_name", "Test")
                        .claim("family_name", keycloakId)
                        .claim("email", keycloakId + "@test.cm"))
                .authorities(Arrays.stream(roles)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(GrantedAuthority[]::new));
    }

    @Test
    @DisplayName("QF-21 : un conflit de champ nomme le champ, un conflit d'état n'en nomme aucun")
    void conflit_nommeLeChampFautif() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        String reference = "CLI-QF21-" + n;
        RequestPostProcessor juriste = role("kc-juriste-qf21", "ROLE_JURISTE", "ROLE_SAISIE");

        mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client QF21 " + n, "reference", reference)))
                        .with(juriste))
                .andExpect(status().isCreated());

        /*
         * ERR-CONFLICT sert à une dizaine de conflits sans rapport entre eux. Sans « champ », un
         * frontend recevant ce code devait deviner la cible depuis l'endpoint appelé — et se
         * trompait dès qu'un endpoint en gagnait un second.
         */
        mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Doublon QF21", "reference", reference)))
                        .with(juriste))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR-CONFLICT"))
                .andExpect(jsonPath("$.champ").value("reference"));

        // Une erreur qui ne vise aucun champ n'en invente pas : la clé est absente du corps, pas
        // présente à null — c'est ce qui rend l'ajout rétrocompatible.
        mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Sans référence")))
                        .with(juriste))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.champ").doesNotExist());
    }

    private String api(String chemin) {
        return "/api/v1" + chemin;
    }

    private String json(Object valeur) throws Exception {
        return objectMapper.writeValueAsString(valeur);
    }

    private JsonNode lireJson(MvcResult resultat) throws Exception {
        return objectMapper.readTree(resultat.getResponse().getContentAsString());
    }

    private long extraireId(MvcResult resultat) throws Exception {
        JsonNode noeud = lireJson(resultat);
        assertThat(noeud.has("id")).as("réponse sans identifiant : %s", noeud).isTrue();
        return noeud.get("id").asLong();
    }
}
