package com.afriland.audiences.integration;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import com.afriland.audiences.referentiel.repository.ProfilRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.mock.web.MockMultipartFile;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired private com.afriland.audiences.dossier.repository.EtapeProcedureRepository etapeProcedureRepository;
    @Autowired private AlerteService alerteService;

    private Long avocatId;

    @BeforeEach
    void setUp() {
        provisionner("kc-juriste-m15", "JURISTE");
        provisionner("kc-dj-m15", "DJ");
        provisionner("kc-dja-m15", "DJA");
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
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("categorie", "RECOUVREMENT"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 500_000),
                                Map.entry("dossierCreditOrigine", "CRD-Q69-" + n),
                                Map.entry("pvTransfert", "PV-Q69-" + n),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)),
                                // Obligatoires depuis le 2026-10-05 (observations 1 et 3).
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))))))
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
        // Q-100 : la réponse est paginée — `{content, totalPages, totalElements}`.
        assertThat(avocats.get("content").findValues("id")).anySatisfy(n -> assertThat(n.asLong()).isEqualTo(id));
        avocats.get("content").forEach(a -> assertThat(a.get("type").asText()).isEqualTo("AVOCAT"));
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
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists());
        mockMvc.perform(get(api("/ged/demandes-suppression"))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE")))
                .andExpect(status().isForbidden());

        // File des constitutions à signer — SH
        mockMvc.perform(get(api("/constitutions/prestataires")).param("statut", "EN_ATTENTE")
                        .with(role("kc-sh-m15", "ROLE_SH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists());
        mockMvc.perform(get(api("/constitutions/prestataires"))
                        .with(role("kc-assistante-m15", "ROLE_ASSISTANTE")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- Sous-ressources d'un dossier

    @Test
    @DisplayName("Les onglets audiences / délibérés / alarmes d'un dossier sont servis, 404 si le dossier n'existe pas")
    void sousRessourcesDossier() throws Exception {
        long dossierId = creerDossier();

        /*
         * Q-100 : audiences et alarmes sont paginées — elles grandissent avec la vie du dossier. Les
         * délibérés restent une liste : un dossier en compte un par étape, trois au maximum. Cette
         * asymétrie est délibérée, et ce test la fige pour qu'elle ne soit pas « corrigée » par
         * mégarde dans un sens ou dans l'autre.
         */
        for (String sousRessource : List.of("audiences", "alarmes")) {
            mockMvc.perform(get(api("/dossiers/" + dossierId + "/" + sousRessource))
                            .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").exists());
        }

        mockMvc.perform(get(api("/dossiers/" + dossierId + "/deliberes"))
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        for (String sousRessource : List.of("audiences", "deliberes", "alarmes")) {
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

    // ---------------------------------------------------------------- Ordre des notifications (Q-92)

    /**
     * Q-92 : {@code GET /alertes/mes-notifications} n'avait aucun {@code ORDER BY}. L'ordre était
     * laissé à PostgreSQL, donc une notification pouvait atterrir sur n'importe quelle page — et
     * une même alerte apparaître deux fois, ou disparaître, en changeant de page. Les trois alertes
     * de ce test sont créées dans la même seconde : seul le départage par {@code id} les ordonne.
     */
    @Test
    @DisplayName("Q-92 : les notifications reviennent de la plus récente à la plus ancienne")
    void mesNotifications_sontTrieesDeLaPlusRecenteALaPlusAncienne() throws Exception {
        String keycloakId = "kc-alertes-" + COMPTEUR.incrementAndGet();
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(role(keycloakId, "ROLE_JURISTE")))
                .andExpect(status().isOk());
        Utilisateur destinataire = utilisateurRepository.findByKeycloakId(keycloakId).orElseThrow();

        for (String texte : List.of("Première", "Deuxième", "Troisième")) {
            alerteService.declencher(TypeAlerte.DOSSIER_INCOMPLET, null, destinataire, texte);
        }

        mockMvc.perform(get(api("/alertes/mes-notifications")).with(role(keycloakId, "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].declencheur").value("Troisième"))
                .andExpect(jsonPath("$.content[1].declencheur").value("Deuxième"))
                .andExpect(jsonPath("$.content[2].declencheur").value("Première"));
    }

    /**
     * Q-93 : {@code GET /clients} et {@code GET /dossiers} paginaient sans {@code ORDER BY}. Passé
     * vingt clients, celui qu'on venait de créer basculait en page 2 — invisible pour son auteur.
     */
    @Test
    @DisplayName("Q-93 : le client et le dossier qu'on vient de créer sont en tête de la première page")
    void clientsEtDossiers_reviennentDuPlusRecentAuPlusAncien() throws Exception {
        RequestPostProcessor juriste = role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION");
        String nom = "Client TRI " + COMPTEUR.incrementAndGet();
        String reference = extraireReference(mockMvc.perform(post(api("/clients")).with(juriste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", nom))))
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(get(api("/clients")).param("size", "1").with(juriste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value(reference));

        long dossierId = creerDossier();
        mockMvc.perform(get(api("/dossiers")).param("size", "1").with(juriste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(dossierId));
    }

    // ---------------------------------------------------------------- Modification d'un client

    /**
     * {@code PUT /clients/{id}} : recommandé en Q-23, ouvert le 2026-09-22. Trois règles, vérifiées
     * ensemble sur la pile réelle : la garde est celle de la création, la référence reste figée même
     * si le corps en porte une autre, et la modification laisse une trace lisible dans l'audit.
     */
    @Test
    @DisplayName("un client se corrige, sa référence reste figée, et la modification est tracée")
    void client_seModifie_referenceFigee_etTrace() throws Exception {
        RequestPostProcessor juriste = role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION");
        var creation = mockMvc.perform(post(api("/clients")).with(juriste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Nom avec coquile"))))
                .andExpect(status().isCreated()).andReturn();
        String reference = extraireReference(creation);
        long clientId = extraireId(creation);

        // L'avocat n'a pas ROLE_SAISIE : il ne corrige pas un client de la banque.
        mockMvc.perform(put(api("/clients/" + clientId))
                        .with(role("kc-avocat-m15", "ROLE_AVOCAT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Intrusion"))))
                .andExpect(status().isForbidden());

        // Le corps porte une autre référence : elle est ignorée, l'identifiant ne bouge pas.
        mockMvc.perform(put(api("/clients/" + clientId)).with(juriste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reference", "AUTRE-" + reference, "nom", "Nom corrigé",
                                "email", "contact@client.cm"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.nom").value("Nom corrigé"))
                .andExpect(jsonPath("$.email").value("contact@client.cm"));

        // Un nom vide est refusé, comme à la création.
        mockMvc.perform(put(api("/clients/" + clientId)).with(juriste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", " "))))
                .andExpect(status().isBadRequest());

        // La trace : hors dossier, avec la référence et les deux valeurs du nom.
        mockMvc.perform(get(api("/historique")).param("perimetre", "HORS_DOSSIER")
                        .param("code", "CLIENT_MODIFIE").param("size", "1")
                        .with(role("kc-dj-m15", "ROLE_DJ", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].details.reference").value(reference))
                .andExpect(jsonPath("$.content[0].details.ancienNom").value("Nom avec coquile"))
                .andExpect(jsonPath("$.content[0].details.nouveauNom").value("Nom corrigé"))
                .andExpect(jsonPath("$.content[0].details.nouvelEmail").value("contact@client.cm"));
    }

    // ---------------------------------------------------------------- Modification d'un intervenant

    /**
     * {@code PUT /intervenants/{id}} (Q-117) : la garde est celle de la création (DJ, DJA), et ni le
     * type ni l'identifiant Keycloak ne bougent, même si le corps en porte d'autres — l'identité se
     * gère dans Keycloak. Le nom et les coordonnées se corrigent, et la modification est tracée.
     */
    @Test
    @DisplayName("Q-117 : un avocat se corrige, son type et son identifiant Keycloak restent figés, et c'est tracé")
    void intervenant_seModifie_typeEtCompteFiges_etTrace() throws Exception {
        RequestPostProcessor dj = role("kc-dj-m15", "ROLE_DJ", "ROLE_CONSULTATION", "ROLE_SAISIE");
        long n = COMPTEUR.incrementAndGet();
        String compte = "kc-q117-" + n;
        long avocatId = extraireId(mockMvc.perform(post(api("/intervenants")).with(dj)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", "Me Coquile " + n, "compteKeycloak", compte))))
                .andExpect(status().isCreated()).andReturn());
        // Le juriste consulte le référentiel mais ne le modifie pas.
        mockMvc.perform(put(api("/intervenants/" + avocatId))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Intrusion"))))
                .andExpect(status().isForbidden());

        // Le corps porte un autre type et un autre compte : ils sont ignorés.
        mockMvc.perform(put(api("/intervenants/" + avocatId)).with(dj).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AUTRE_PRESTATAIRE", "nom", "Me Corrigé " + n,
                                "email", "corrige@cab.cm", "compteKeycloak", compte + "-bis"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("AVOCAT"))
                .andExpect(jsonPath("$.nom").value("Me Corrigé " + n))
                .andExpect(jsonPath("$.email").value("corrige@cab.cm"))
                .andExpect(jsonPath("$.compteKeycloak").value(compte));

        mockMvc.perform(get(api("/historique")).param("perimetre", "HORS_DOSSIER")
                        .param("code", "INTERVENANT_MODIFIE").param("size", "1")
                        .with(role("kc-dj-m15", "ROLE_DJ", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].details.prestataireId").value(avocatId))
                .andExpect(jsonPath("$.content[0].details.nouveauNom").value("Me Corrigé " + n))
                .andExpect(jsonPath("$.content[0].details.nouvelEmail").value("corrige@cab.cm"))
                .andExpect(jsonPath("$.content[0].details.nouveauCompteKeycloak").doesNotExist());
    }

    // ---------------------------------------------------------------- Vue consolidée par client (QF-41)

    /**
     * QF-41 : les étapes, juristes et avocats de la vue consolidée sont désormais chargés en trois
     * requêtes pour toute la liste, au lieu de trois par dossier. Le risque d'une telle bascule est
     * l'appariement — qu'un dossier hérite des affectations d'un autre. Ce parcours le vérifie sur
     * deux dossiers du même client, affectés à des avocats différents.
     */
    @Test
    @DisplayName("QF-41 : chaque dossier de la vue consolidée garde ses propres étapes et affectations")
    void vueConsolidee_apparieChaqueDossierAvecSesPropresAffectations() throws Exception {
        RequestPostProcessor juriste = role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION");
        int n = COMPTEUR.incrementAndGet();
        long clientId = extraireId(mockMvc.perform(post(api("/clients")).with(juriste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client QF41 " + n, "reference", "CLI-QF41-" + n))))
                .andExpect(status().isCreated())
                .andReturn());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m15").orElseThrow().getId();
        long autreAvocatId = extraireId(mockMvc.perform(post(api("/intervenants"))
                        .with(role("kc-dj-qf41", "ROLE_DJ", "ROLE_SAISIE", "ROLE_CONSULTATION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        // RG-INT-01 : un avocat porte obligatoirement un compte.
                        .content(json(Map.of("type", "AVOCAT", "nom", "Me QF41 " + n,
                                "compteKeycloak", "kc-avocat-qf41-" + n))))
                .andExpect(status().isCreated())
                .andReturn());

        long premier = creerDossierPourClient(juriste, clientId, juristeId, avocatId, "DOS-QF41-A-" + n);
        long second = creerDossierPourClient(juriste, clientId, juristeId, autreAvocatId, "DOS-QF41-B-" + n);

        JsonNode vue = lireJson(mockMvc.perform(get(api("/clients/" + clientId + "/dossiers")).with(juriste))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode recouvrement = vue.get("recouvrement");
        assertThat(recouvrement).hasSize(2);
        recouvrement.forEach(dossier -> {
            long attendu = dossier.get("id").asLong() == premier ? avocatId : autreAvocatId;
            assertThat(dossier.get("avocatsAffectes").get(0).asLong())
                    .as("le dossier %s doit porter son propre avocat", dossier.get("reference").asText())
                    .isEqualTo(attendu);
            assertThat(dossier.get("juristesAffectes").get(0).asLong()).isEqualTo(juristeId);
            // Chaque dossier naît avec son étape INSTANCE : la liste ne doit être ni vide ni doublée.
            assertThat(dossier.get("etapes")).hasSize(1);
        });
        assertThat(recouvrement.get(0).get("id").asLong()).isNotEqualTo(recouvrement.get(1).get("id").asLong());
        assertThat(second).isNotEqualTo(premier);
    }

    private long creerDossierPourClient(RequestPostProcessor juriste, long clientId, Long juristeId,
                                          long avocat, String reference) throws Exception {
        return extraireId(mockMvc.perform(post(api("/dossiers")).with(juriste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("categorie", "RECOUVREMENT"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-" + reference),
                                Map.entry("pvTransfert", "PV-" + reference),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocat)),
                                // Obligatoires depuis le 2026-10-05 (observations 1 et 3).
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur " + reference)))))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    // ---------------------------------------------------------------- Portail Avocat (Q-96)

    /**
     * Q-96 : le point sensible du portail n'est pas ce que l'avocat voit, c'est ce qu'il ne doit
     * <b>pas</b> voir. Ce parcours crée deux dossiers — un auquel il est affecté, un auquel il ne
     * l'est pas — et vérifie que le second n'apparaît ni dans sa liste ni par son identifiant.
     */
    @Test
    @DisplayName("Q-96 : l'avocat ne voit que les dossiers auxquels il est affecté")
    void portailAvocat_neVoitQueSesPropresDossiers() throws Exception {
        RequestPostProcessor avocat = role("kc-avocat-m15", "ROLE_AVOCAT");
        long sien = creerDossier();
        long autre = creerDossierSansAvocat();

        JsonNode mesDossiers = lireJson(mockMvc.perform(get(api("/avocat/mes-dossiers")).param("size", "100").with(avocat))
                        .andExpect(status().isOk())
                        .andReturn());
        List<Long> identifiants = mesDossiers.get("content").findValues("id").stream().map(JsonNode::asLong).toList();
        assertThat(identifiants).contains(sien).doesNotContain(autre);

        // La réponse reste réduite à l'identification : ni client, ni risque, ni sensibilité.
        JsonNode premier = mesDossiers.get("content").get(0);
        assertThat(premier.has("reference")).isTrue();
        assertThat(premier.has("clientId")).as("le portefeuille de la banque ne doit pas fuiter").isFalse();
        assertThat(premier.has("risqueEncouru")).isFalse();
        assertThat(premier.has("estSensible")).isFalse();

        // Les audiences de son dossier lui sont ouvertes, celles d'un autre non (403, pas 404).
        mockMvc.perform(get(api("/avocat/mes-dossiers/" + sien + "/audiences")).with(avocat))
                .andExpect(status().isOk());
        mockMvc.perform(get(api("/avocat/mes-dossiers/" + autre + "/audiences")).with(avocat))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Q-96 : le portail est fermé aux profils internes, qui ont leurs propres écrans")
    void portailAvocat_estFermeAuxProfilsInternes() throws Exception {
        for (String[] profil : List.of(new String[] {"kc-juriste-m15", "ROLE_JURISTE"},
                                        new String[] {"kc-dj-m15", "ROLE_DJ"},
                                        new String[] {"kc-assistante-m15", "ROLE_ASSISTANTE"})) {
            mockMvc.perform(get(api("/avocat/mes-dossiers")).with(role(profil[0], profil[1], "ROLE_CONSULTATION")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Q-96 : ses publications et ses constitutions lui reviennent, les siennes seules")
    void portailAvocat_relitSesPublicationsEtSesConstitutions() throws Exception {
        RequestPostProcessor avocat = role("kc-avocat-m15", "ROLE_AVOCAT");

        mockMvc.perform(get(api("/avocat/mes-publications")).with(avocat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        JsonNode constitutions = lireJson(mockMvc.perform(get(api("/avocat/mes-constitutions")).with(avocat))
                .andExpect(status().isOk())
                .andReturn());
        constitutions.forEach(demande ->
                assertThat(demande.get("prestataireId").asLong()).isEqualTo(avocatId));
    }

    /**
     * Q-97 : UC-INT-01 étape 4 veut que l'avocat <b>charge</b> ses pièces justificatives, et le
     * dictionnaire décrit {@code pieces_justificatives} comme des « chemins ou références ». Ce
     * parcours va du téléversement jusqu'à la relecture par l'instructeur (UC-INT-02 étape 3).
     */
    @Test
    @DisplayName("Q-97 : l'avocat charge une pièce, la demande la porte, l'instructeur peut l'ouvrir")
    void pieceJustificative_estChargeePuisRelisibleParLInstructeur() throws Exception {
        RequestPostProcessor avocat = role("kc-avocat-m15", "ROLE_AVOCAT");
        long dossierId = creerDossier();

        JsonNode piece = lireJson(mockMvc.perform(multipart(api("/avocat/pieces-justificatives"))
                        .file(new MockMultipartFile("file", "facture.pdf", "application/pdf", new byte[] {1, 2, 3}))
                        .with(avocat))
                .andExpect(status().isCreated())
                .andReturn());
        String cle = piece.get("cle").asText();
        assertThat(cle).startsWith("frais/");
        assertThat(piece.get("nomFichier").asText()).isEqualTo("facture.pdf");

        long demandeId = extraireId(mockMvc.perform(post(api("/frais/demandes")).with(avocat)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dossierId", dossierId, "montant", 250_000,
                                "piecesJustificatives", List.of(cle),
                                "referenceFacture", "FAC-Q97-" + COMPTEUR.incrementAndGet()))))
                .andExpect(status().isCreated())
                .andReturn());

        // L'Assistante instruit : elle doit voir la pièce, pas en lire le nom (UC-INT-02 étape 3).
        JsonNode detail = lireJson(mockMvc.perform(get(api("/frais/demandes/" + demandeId))
                        .with(role("kc-assistante-m15", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.get("piecesTelechargeables")).hasSize(1);
        assertThat(detail.get("piecesTelechargeables").get(0).get("nomFichier").asText()).isEqualTo("facture.pdf");
        // Le champ est bien renseigné ; sa valeur vient de MinIO, dont le client est mocké ici.
        assertThat(detail.get("piecesTelechargeables").get(0).has("urlTelechargement")).isTrue();
    }

    /** Q-97 : un format hors FORMATS_GED est refusé — mêmes règles que la GED, un seul réglage. */
    @Test
    @DisplayName("Q-97 : une pièce d'un format non autorisé est refusée")
    void pieceJustificative_refuseUnFormatNonAutorise() throws Exception {
        mockMvc.perform(multipart(api("/avocat/pieces-justificatives"))
                        .file(new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] {1}))
                        .with(role("kc-avocat-m15", "ROLE_AVOCAT")))
                .andExpect(status().isBadRequest());
    }

    /** Un dossier du même client, affecté à un autre avocat : le témoin de la restriction. */
    private long creerDossierSansAvocat() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client Q96 " + n, "reference", "CLI-Q96-" + n)))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());
        long autreAvocat = extraireId(mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", "Me Autre Q96 " + n,
                                "compteKeycloak", "kc-autre-avocat-q96-" + n)))
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isCreated())
                .andReturn());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m15").orElseThrow().getId();
        return extraireId(mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("categorie", "RECOUVREMENT"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-Q96-" + n),
                                Map.entry("pvTransfert", "PV-Q96-" + n),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(autreAvocat)),
                                // Obligatoires depuis le 2026-10-05 (observations 1 et 3).
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur " + n))))))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());
    }

    // ---------------------------------------------------------------- Helpers

    // ---------------------------------------------------------------- Pagination (Q-100)

    /**
     * L'exigence du porteur du projet, vérifiée là où elle se joue : **dix lignes par page, et la
     * onzième sur la page suivante**.
     *
     * <p>Ce test ne se contente pas de compter dix lignes — une page qui perdrait la onzième
     * passerait encore. Il vérifie que la onzième **existe**, qu'elle est **sur la page 2**, et
     * qu'elle est **différente** de celles de la page 1 : c'est la garantie « aucune perte de
     * données » prise au mot.
     */
    @Test
    @DisplayName("Q-100 : onze alarmes tiennent sur deux pages, sans perte ni doublon")
    void paginationDixLignes_laOnziemeEstSurLaPageSuivante() throws Exception {
        long dossierId = creerDossier();
        long etapeId = lireJson(mockMvc.perform(get(api("/dossiers/" + dossierId))
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn())
                .get("etapes").get(0).get("id").asLong();

        for (int i = 1; i <= 11; i++) {
            mockMvc.perform(post(api("/dossiers/" + dossierId + "/alarmes"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("etapeId", etapeId, "objet", "Alarme " + i,
                                    "dateEcheance", LocalDate.now().plusDays(i).atStartOfDay().toString())))
                            .with(role("kc-juriste-m15", "ROLE_JURISTE")))
                    .andExpect(status().isCreated());
        }

        JsonNode page1 = lireJson(mockMvc.perform(get(api("/dossiers/" + dossierId + "/alarmes"))
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andReturn());

        JsonNode page2 = lireJson(mockMvc.perform(get(api("/dossiers/" + dossierId + "/alarmes"))
                        .param("page", "1")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andReturn());

        var idsPage1 = page1.get("content").findValues("id").stream().map(JsonNode::asLong).toList();
        var idsPage2 = page2.get("content").findValues("id").stream().map(JsonNode::asLong).toList();
        assertThat(idsPage1).hasSize(10).doesNotContainAnyElementsOf(idsPage2);
        assertThat(idsPage2).hasSize(1);
    }

    /** Q-101 — le filtre demandé par le porteur du projet : la file des sensibilités à valider. */
    @Test
    @DisplayName("Q-101 : les dossiers sensibles et leur statut de validation sont filtrables")
    void filtreSensibilite() throws Exception {
        creerDossier();

        mockMvc.perform(get(api("/dossiers")).param("estSensible", "true")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get(api("/dossiers")).param("sensibiliteStatut", "EN_ATTENTE")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    /** Q-105 — la recherche au fil de la frappe des sélecteurs. */
    @Test
    @DisplayName("Q-105 : le référentiel se cherche par fragment de nom")
    void rechercheIntervenantParNom() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        String nom = "Me Rechercheable " + n;
        mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", nom,
                                "compteKeycloak", "kc-recherche-" + n)))
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isCreated());

        // Fragment au milieu du nom : c'est ce qu'un index B-tree ne sait pas servir (Q-106).
        mockMvc.perform(get(api("/intervenants")).param("nom", "Rechercheable")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get(api("/intervenants")).param("nom", "IntrouvableXYZ")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

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
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("categorie", "RECOUVREMENT"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-M15-" + n),
                                Map.entry("pvTransfert", "PV-M15-" + n),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)),
                                // Obligatoires depuis le 2026-10-05 (observations 1 et 3).
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))))))
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
    @Test
    @DisplayName("Q-84 : un avocat est reconnu par son identifiant de connexion, et l'utilisateur le garde")
    void avocat_estReconnuParSonIdentifiantDeConnexion() throws Exception {
        String identifiant = "avocat.q84." + COMPTEUR.incrementAndGet();
        avocatRepository.save(Avocat.builder().nom("Me Q84").compteKeycloak(identifiant).build());

        mockMvc.perform(get(api("/frais/demandes")).with(avocatConnecte("kc-sub-" + identifiant, identifiant)))
                .andExpect(status().isOk());
        assertThat(utilisateurRepository.findByKeycloakId("kc-sub-" + identifiant))
                .get().extracting(Utilisateur::getIdentifiant).isEqualTo(identifiant);

        // Un compte sans avocat rattaché, ni par identifiant ni par sub, reste refusé.
        mockMvc.perform(get(api("/frais/demandes")).with(avocatConnecte("kc-sub-inconnu-" + identifiant, "inconnu." + identifiant)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Q-88 : les décisions d'un dossier se relisent, statut de paiement compris ; 404 si le dossier est inconnu")
    void decisionsDuDossier_seRelisent() throws Exception {
        long dossierId = creerDossier();
        RequestPostProcessor juriste = role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_CONSULTATION");
        RequestPostProcessor assistante = role("kc-assistante-m15", "ROLE_ASSISTANTE", "ROLE_CONSULTATION");

        long condamnationId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/condamnations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sens", "TIERS_REDEVABLE", "naturePaiement", "Dommages et intérêts",
                                "dateDecision", LocalDate.now().toString())))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(patch(api("/condamnations/" + condamnationId + "/statut"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("statutPaiement", "RECOUVREMENT_FORCE")))
                        .with(juriste))
                .andExpect(status().isOk());

        mockMvc.perform(get(api("/dossiers/" + dossierId + "/decisions")).with(assistante))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjudications.length()").value(0))
                .andExpect(jsonPath("$.condamnations.length()").value(1))
                .andExpect(jsonPath("$.condamnations[0].id").value(condamnationId))
                .andExpect(jsonPath("$.condamnations[0].statutPaiement").value("RECOUVREMENT_FORCE"));

        mockMvc.perform(get(api("/dossiers/999999/decisions")).with(assistante))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Q-89 : la recherche jurisprudentielle est partielle, insensible à la casse, et filtrable par dossier")
    void jurisprudences_rechercheSouple() throws Exception {
        long dossierId = creerDossier();
        /*
         * Depuis le 2026-09-29, une décision ne rejoint la base jurisprudentielle qu'une fois
         * définitive : plus aucune étape du dossier en cours. Ce parcours porte sur la recherche,
         * pas sur le cycle de vie — dérouler audience, délibéré et clôture n'y prouverait rien de
         * plus. Les étapes sont donc closes directement, comme P2 antidate son audience.
         */
        cloreLesEtapes(dossierId);
        String marque = "Q89M" + COMPTEUR.incrementAndGet();
        RequestPostProcessor consultation = role("kc-assistante-m15", "ROLE_CONSULTATION");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart(api("/jurisprudences"))
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "decision.pdf", "application/pdf", new byte[]{1, 2, 3}))
                        .param("dossierId", String.valueOf(dossierId))
                        .param("typeArchive", "GROSSE")
                        .param("motsCles", "Saisie immobilière " + marque)
                        .param("motsCles", "Adjudication")
                        .param("natureDecision", "Jugement")
                        .param("juridiction", "TGI Douala")
                        .param("dateDecision", "2026-01-15")
                        .with(role("kc-juriste-m15", "ROLE_JURISTE")))
                .andExpect(status().isCreated());

        // Un fragment de mot-clé, en minuscules, suffit.
        mockMvc.perform(get(api("/jurisprudences")).param("motCle", marque.toLowerCase()).with(consultation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        // Juridiction en minuscules, restreinte au dossier.
        mockMvc.perform(get(api("/jurisprudences")).param("motCle", marque).param("juridiction", "tgi douala")
                        .param("dossierId", String.valueOf(dossierId)).with(consultation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        // Un autre dossier ne la contient pas.
        mockMvc.perform(get(api("/jurisprudences")).param("motCle", marque).param("dossierId", "999999").with(consultation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** Jeton d'avocat tel que Keycloak l'émet : avec son identifiant de connexion (Q-84). */
    private RequestPostProcessor avocatConnecte(String sub, String identifiant) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .subject(sub)
                        .claim("roles", List.of("ROLE_AVOCAT"))
                        .claim("preferred_username", identifiant)
                        .claim("given_name", "Test")
                        .claim("family_name", identifiant)
                        .claim("email", identifiant + "@test.cm"))
                .authorities(new SimpleGrantedAuthority("ROLE_AVOCAT"));
    }

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

        String compte = "kc-avocat-qf21-" + n;
        mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", "Me QF21 " + n,
                                "email", compte + "@cabinet.cm", "compteKeycloak", compte)))
                        .with(role("kc-dj-qf21-" + n, "ROLE_DJ")))
                .andExpect(status().isCreated());

        /*
         * ERR-CONFLICT sert à une dizaine de conflits sans rapport entre eux. Sans « champ », un
         * frontend recevant ce code devait deviner la cible depuis l'endpoint appelé — et se
         * trompait dès qu'un endpoint en gagnait un second.
         *
         * Le cas jouait sur une référence client en doublon jusqu'au 2026-09-29 ; le système
         * attribuant désormais les références, ce conflit-là ne peut plus se produire. Le compte
         * Keycloak d'un avocat, lui, reste saisi (RG-INT-01) et porte le même besoin.
         */
        mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", "Doublon QF21",
                                "email", "doublon-qf21-" + n + "@cabinet.cm", "compteKeycloak", compte)))
                        .with(role("kc-dj-qf21-" + n, "ROLE_DJ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR-CONFLICT"))
                .andExpect(jsonPath("$.champ").value("compteKeycloak"));

        // Une erreur qui ne vise aucun champ n'en invente pas : la clé est absente du corps, pas
        // présente à null — c'est ce qui rend l'ajout rétrocompatible.
        mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "sans-nom@client.cm")))
                        .with(juriste))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.champ").doesNotExist());
    }

    @Test
    @DisplayName("M16 : les trois natures longues de la taxonomie sont creables (Q-71)")
    void naturesLongues_sontDesormaisAcceptees() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        RequestPostProcessor juriste = role("kc-juriste-m16", "ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION");

        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client M16 " + n, "reference", "CLI-M16-" + n)))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());
        // Un compte n'existe en base qu'apres son premier appel authentifie (correctif R-06) :
        // creer un client ne suffit pas, il faut une lecture qui passe par getCurrentUser.
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(juriste)).andExpect(status().isOk());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m16").orElseThrow().getId();
        long avocatId = avocatRepository.save(Avocat.builder().nom("Me M16 " + n)
                .compteKeycloak("kc-avocat-m16-nature-" + n).build()).getId();

        /*
         * 71 caracteres : au-dela de l'ancienne limite de 50 imposee a `dossier.nature`, alors meme
         * que ce libelle vient du referentiel que le service interroge. Trois des dix-sept natures
         * etaient dans ce cas et rendaient la creation impossible.
         */
        String natureLongue = "Hypothèque judiciaire (inscription provisoire, définitive, réalisation)";
        assertThat(natureLongue.length()).isGreaterThan(50);

        mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("nature", natureLongue),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-" + n),
                                Map.entry("pvTransfert", "PV-" + n),
                                Map.entry("estSensible", true),
                                Map.entry("seuilMontant", 60_000_000),
                                Map.entry("typeClientSensible", "VIP"),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)),
                                // Obligatoires depuis le 2026-10-05 (observations 1 et 3).
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))))))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nature").value(natureLongue))
                // La categorie n'a pas ete soumise : elle est deduite de la nature (RG-DOS-02).
                .andExpect(jsonPath("$.categorie").value("RECOUVREMENT"))
                .andExpect(jsonPath("$.typeClientSensible").value("VIP"));
    }

    @Test
    @DisplayName("M16 : un type de client sensible hors referentiel est refuse (Q-73)")
    void typeClientSensibleInconnu_estRefuse() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        RequestPostProcessor juriste = role("kc-juriste-m16b", "ROLE_JURISTE", "ROLE_SAISIE");

        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client M16b " + n, "reference", "CLI-M16B-" + n)))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());
        // Un compte n'existe en base qu'apres son premier appel authentifie (correctif R-06) :
        // creer un client ne suffit pas, il faut une lecture qui passe par getCurrentUser.
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(juriste)).andExpect(status().isOk());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m16b").orElseThrow().getId();
        long avocatId = avocatRepository.save(Avocat.builder().nom("Me M16b " + n)
                .compteKeycloak("kc-avocat-m16b-" + n).build()).getId();

        mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-" + n),
                                Map.entry("pvTransfert", "PV-" + n),
                                Map.entry("estSensible", true),
                                Map.entry("seuilMontant", 60_000_000),
                                Map.entry("typeClientSensible", "VIPP"),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)),
                                // Obligatoires depuis le 2026-10-05 (observations 1 et 3).
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))))))
                        .with(juriste))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("inconnu du référentiel")));
    }

    @Test
    @DisplayName("M16 : recherche par reference, par client et « mes dossiers » (Q-72)")
    void rechercheDeDossiers_parReferenceClientEtPortefeuille() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        RequestPostProcessor juriste = role("kc-juriste-m16c", "ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION");

        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client M16c " + n, "reference", "CLI-M16C-" + n)))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());
        // Un compte n'existe en base qu'apres son premier appel authentifie (correctif R-06) :
        // creer un client ne suffit pas, il faut une lecture qui passe par getCurrentUser.
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(juriste)).andExpect(status().isOk());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m16c").orElseThrow().getId();
        long avocatId = avocatRepository.save(Avocat.builder().nom("Me M16c " + n)
                .compteKeycloak("kc-avocat-m16c-" + n).build()).getId();

        String reference = extraireReference(mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-" + n),
                                Map.entry("pvTransfert", "PV-" + n),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)),
                                // Obligatoires depuis le 2026-10-05 (observations 1 et 3).
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))))))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());

        /*
         * Reference : recherche partielle, on ne retient jamais une reference en entier. Depuis le
         * 2026-09-29 elle est attribuee par le systeme, le test cherche donc un fragment de la
         * reference recue — l'annee et le numero, sans le prefixe.
         */
        mockMvc.perform(get(api("/dossiers")).param("reference", reference.substring(4)).with(juriste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reference").value(reference));

        // Client : tous les dossiers portes contre lui.
        mockMvc.perform(get(api("/dossiers")).param("clientId", String.valueOf(clientId)).with(juriste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        /*
         * « Mes dossiers » se resout sur l'utilisateur authentifie. Le meme appel joue par un autre
         * juriste ne doit donc rien renvoyer : c'est ce qui garantit que le filtre ne sert pas a
         * consulter le portefeuille d'un collegue.
         */
        mockMvc.perform(get(api("/dossiers")).param("mesDossiers", "true")
                        .param("reference", reference.substring(4)).with(juriste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get(api("/dossiers")).param("mesDossiers", "true")
                        .param("reference", reference.substring(4))
                        .with(role("kc-juriste-m16d", "ROLE_JURISTE", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("M16 : une dérogation demandée se lit, puis s'arbitre ; la proposition initiale ne s'arbitre pas (Q-74)")
    void derogation_lueEtArbitrable() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        RequestPostProcessor juriste = role("kc-juriste-q74", "ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION");
        RequestPostProcessor dj = role("kc-dj-q74", "ROLE_DJ", "ROLE_CONSULTATION");

        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client Q74 " + n, "reference", "CLI-Q74-" + n)))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(juriste)).andExpect(status().isOk());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-q74").orElseThrow().getId();
        long avocatId = avocatRepository.save(Avocat.builder().nom("Me Q74 " + n)
                .compteKeycloak("kc-avocat-q74-" + n).build()).getId();

        long dossierId = extraireId(mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-" + n),
                                Map.entry("pvTransfert", "PV-" + n),
                                Map.entry("estSensible", true),
                                Map.entry("seuilMontant", 60_000_000),
                                Map.entry("typeClientSensible", "VIP"),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)),
                                // Obligatoires depuis le 2026-10-05 (observations 1 et 3).
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))))))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());

        // Avant validation : la seule demande est la proposition initiale — lisible, mais pas
        // arbitrable comme une dérogation. Elle se tranche par la validation de sensibilité.
        JsonNode initiales = lireJson(mockMvc.perform(get(api("/dossiers/" + dossierId + "/seuil-derogation")).with(dj))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(initiales).hasSize(1);
        long propositionInitiale = initiales.get(0).get("id").asLong();
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/seuil-derogation/" + propositionInitiale))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("statut", "VALIDEE")))
                        .with(dj))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(api("/dossiers/" + dossierId + "/sensibilite/validation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("statut", "VALIDEE")))
                        .with(dj))
                .andExpect(status().isOk());

        mockMvc.perform(post(api("/dossiers/" + dossierId + "/seuil-derogation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nouveauSeuil", 80_000_000, "motif", "Client stratégique")))
                        .with(juriste))
                .andExpect(status().isCreated());

        // Le DJ lit la file d'arbitrage : c'est ce qui manquait (QF-23).
        JsonNode enAttente = lireJson(mockMvc.perform(get(api("/dossiers/" + dossierId + "/seuil-derogation"))
                        .param("statut", "EN_ATTENTE").with(dj))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(enAttente).hasSize(1);
        assertThat(enAttente.get(0).get("nouveauSeuil").asDouble()).isEqualTo(80_000_000d);
        long demande = enAttente.get(0).get("id").asLong();

        mockMvc.perform(put(api("/dossiers/" + dossierId + "/seuil-derogation/" + demande))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("statut", "VALIDEE")))
                        .with(dj))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDEE"));

        mockMvc.perform(get(api("/dossiers/" + dossierId + "/seuil-derogation")).param("statut", "EN_ATTENTE").with(dj))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(api("/dossiers/" + dossierId + "/seuil-derogation")).with(dj))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("M16 : l'affectation ne peut plus vider un dossier de ses juristes ni de ses avocats (Q-75)")
    void affectation_exigeUnJuristeEtUnAvocat() throws Exception {
        long dossierId = creerDossier();
        RequestPostProcessor juriste = role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE");
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m15").orElseThrow().getId();

        // L'endpoint remplace les listes : une liste vide retirait tout le monde, contre RG-DOS-03.
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/affectation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("juristesAffectes", List.of(), "avocatsAffectes", List.of(avocatId))))
                        .with(juriste))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-VALIDATION"))
                .andExpect(jsonPath("$.details").value(containsString("juristesAffectes")));

        // Une liste absente vaut une liste vide : même refus.
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/affectation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("juristesAffectes", List.of(juristeId))))
                        .with(juriste))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("avocatsAffectes")));

        // Le cas nominal passe toujours, confirmation du doublon comprise.
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/affectation")).param("forcer", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("juristesAffectes", List.of(juristeId), "avocatsAffectes", List.of(avocatId))))
                        .with(juriste))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("M16 : une audience annulée quitte le calendrier, ne compte plus comme doublon et ne reçoit pas de compte rendu (Q-76)")
    void audienceAnnulee_sortDuCalendrierEtDesDoublons() throws Exception {
        long dossierId = creerDossier();
        RequestPostProcessor juriste = role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_CONSULTATION");
        long etapeId = etapeEnCours(dossierId, juriste);

        String planification = json(Map.of("etapeId", etapeId, "datePlanifiee", LocalDate.now().plusDays(10).toString()));
        long audienceId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/audiences"))
                        .contentType(MediaType.APPLICATION_JSON).content(planification).with(juriste))
                .andExpect(status().isCreated())
                .andReturn());

        // Le motif est obligatoire, et l'annulation réservée au juriste, comme la planification.
        mockMvc.perform(patch(api("/audiences/" + audienceId + "/annulation"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("motif", " "))).with(juriste))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(api("/audiences/" + audienceId + "/annulation"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("motif", "Renvoi")))
                        .with(role("kc-dj-m15", "ROLE_DJ", "ROLE_CONSULTATION")))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(api("/audiences/" + audienceId + "/annulation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("motif", "Renvoi prononcé par le tribunal"))).with(juriste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"))
                .andExpect(jsonPath("$.motifAnnulation").value("Renvoi prononcé par le tribunal"))
                .andExpect(jsonPath("$.dateAnnulation").isNotEmpty());

        // Une audience déjà annulée ne s'annule pas une seconde fois.
        mockMvc.perform(patch(api("/audiences/" + audienceId + "/annulation"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("motif", "Encore"))).with(juriste))
                .andExpect(status().isBadRequest());

        /*
         * Replanifier à la même date après un renvoi n'est pas un doublon : 201 direct, sans
         * ?forcer — l'audience annulée aurait sinon déclenché ERR-005 et une alerte au DJ.
         */
        long nouvelleId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/audiences"))
                        .contentType(MediaType.APPLICATION_JSON).content(planification).with(juriste))
                .andExpect(status().isCreated())
                .andReturn());

        // Le calendrier — donc ses exports, qui en reprennent les entrées — écarte l'annulée et garde
        // la replanifiée : le filtre ne se contente pas de tout vider.
        JsonNode calendrier = lireJson(mockMvc.perform(get(api("/audiences/calendrier"))
                        .param("periode", "TRIMESTRIELLE").param("dateDebut", LocalDate.now().toString())
                        .with(juriste))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(calendrier.findValues("audienceId").stream().map(JsonNode::asLong).toList())
                .contains(nouvelleId)
                .doesNotContain(audienceId);

        // Une audience annulée ne reçoit pas de compte rendu — refus propre, avant même la règle de date.
        mockMvc.perform(put(api("/audiences/" + audienceId + "/compte-rendu"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("compteRendu", "CR")))
                        .with(juriste))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("annulée")));
    }

    @Test
    @DisplayName("M16 : une alarme se clôt sans être remplacée, une seule fois, par un juriste (Q-77)")
    void alarme_traiteeSansRemplacante() throws Exception {
        long dossierId = creerDossier();
        RequestPostProcessor juriste = role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_CONSULTATION");
        long etapeId = etapeEnCours(dossierId, juriste);

        long alarmeId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/alarmes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "objet", "Relancer le greffe",
                                "dateEcheance", LocalDateTime.now().plusDays(3).withNano(0).toString())))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(patch(api("/alarmes/" + alarmeId + "/traiter"))
                        .with(role("kc-dj-m15", "ROLE_DJ", "ROLE_CONSULTATION")))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(api("/alarmes/" + alarmeId + "/traiter")).with(juriste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alarmeId))
                .andExpect(jsonPath("$.statut").value("TRAITEE"))
                .andExpect(jsonPath("$.dateTraitement").isNotEmpty());

        mockMvc.perform(patch(api("/alarmes/" + alarmeId + "/traiter")).with(juriste))
                .andExpect(status().isBadRequest());

        // Contrairement à la reprogrammation, aucune alarme de remplacement n'a été créée.
        mockMvc.perform(get(api("/dossiers/" + dossierId + "/alarmes")).with(juriste))
                .andExpect(status().isOk())
                // Paginé depuis Q-100 : `$.length()` compterait les trois clés de l'enveloppe.
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Passe la première étape du dossier « en cours » : la condition d'une audience (RG-AUD-01). */
    private long etapeEnCours(long dossierId, RequestPostProcessor juriste) throws Exception {
        long etapeId = lireJson(mockMvc.perform(get(api("/dossiers/" + dossierId)).with(juriste))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("etapes").get(0).get("id").asLong();
        mockMvc.perform(patch(api("/dossiers/" + dossierId + "/etapes/" + etapeId + "/statut"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nouveauStatut", "EN_COURS")))
                        .with(juriste))
                .andExpect(status().isOk());
        return etapeId;
    }

    @Test
    @DisplayName("M16 : une suppression directe ferme la demande en attente, et la file nomme fichier et dossier (Q-79)")
    void suppressionDirecte_fermeLaDemandeEnAttente() throws Exception {
        long dossierId = creerDossier();
        RequestPostProcessor juriste = role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION");
        RequestPostProcessor dj = role("kc-dj-m15", "ROLE_DJ", "ROLE_CONSULTATION");
        String nom = "piece-q79-" + COMPTEUR.incrementAndGet() + ".pdf";

        long documentId = extraireId(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart(api("/ged/documents"))
                        .file(new org.springframework.mock.web.MockMultipartFile("file", nom, "application/pdf", new byte[]{1, 2}))
                        .param("dossierId", String.valueOf(dossierId))
                        .param("typeDocument", "PV_TRANSFERT")
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());

        // Le juriste auteur demande (202) : la demande nomme déjà son fichier et son dossier.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(api("/ged/documents/" + documentId)).with(juriste))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentNom").value(nom))
                .andExpect(jsonPath("$.dossierId").value(dossierId));

        // Le DJ supprime directement le même document (200).
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(api("/ged/documents/" + documentId)).with(dj))
                .andExpect(status().isOk());

        // Plus de demande fantôme en attente : elle est close avec le document, validée par le DJ.
        JsonNode enAttente = lireJson(mockMvc.perform(get(api("/ged/demandes-suppression"))
                        .param("statut", "EN_ATTENTE").with(dj))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(enAttente.findValues("documentId").stream().map(JsonNode::asLong).toList()).doesNotContain(documentId);

        /*
         * `size` explicite : la file est paginée depuis Q-100, et ce test cherche **une** demande
         * précise dans tout l'historique. S'en remettre à la page par défaut le rendrait dépendant du
         * nombre de demandes créées par les autres tests — vert aujourd'hui, rouge le jour où la
         * classe en compte onze.
         */
        JsonNode toutes = lireJson(mockMvc.perform(get(api("/ged/demandes-suppression"))
                        .param("size", "200").with(dj))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode demande = null;
        for (JsonNode candidate : toutes.get("content")) {
            if (candidate.get("documentId").asLong() == documentId) {
                demande = candidate;
            }
        }
        assertThat(demande).as("la demande reste dans l'historique").isNotNull();
        assertThat(demande.get("statut").asText()).isEqualTo("APPROUVEE");
        assertThat(demande.get("documentNom").asText()).isEqualTo(nom);
        // La reference est attribuee par le systeme depuis le 2026-09-29 : le test ne la choisit
        // plus, il verifie sa forme.
        assertThat(demande.get("referenceDossier").asText()).matches("DOS-\\d{4}-\\d{4,}");
    }

    @Test
    @DisplayName("M16 : le rapport journalier est servi au juriste, au DJ, à la DJA et à l'Assistante, refusé au SH (Q-80)")
    void rapportJournalier_ouvertAuxQuatreProfils() throws Exception {
        for (String roleProfil : List.of("ROLE_JURISTE", "ROLE_DJ", "ROLE_DJA", "ROLE_ASSISTANTE")) {
            mockMvc.perform(get(api("/ged/rapport-journalier")).param("date", "2020-01-02")
                            .with(role("kc-rapport-" + roleProfil, roleProfil)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.piecesAjoutees").value(0));
        }
        mockMvc.perform(get(api("/ged/rapport-journalier")).with(role("kc-sh-m15", "ROLE_SH")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- Administration (Q-108)

    /**
     * Q-108 — l'écriture des paramètres système est ouverte au DJ <b>et</b> à la DJA.
     *
     * <p>Ces deux endpoints n'avaient <b>aucun</b> test d'habilitation : la restriction ne tenait
     * qu'à une annotation, sur une règle que la documentation énonce pourtant explicitement. Le
     * constat a été fait en relevant les habilitations DJ/DJA à la demande du porteur du projet ;
     * l'ouverture à la DJA est sa décision, motivée par la continuité de service — le DJ absent,
     * personne ne pouvait plus relever un seuil.
     */
    @Test
    @DisplayName("Q-108 : le DJ et la DJA écrivent les paramètres système, les autres profils sont refusés")
    void configurations_ecriture_ouverteAuDjEtALaDja() throws Exception {
        mockMvc.perform(put(api("/configurations/SEUIL_PROROGATIONS"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("valeur", "11")))
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeur").value("11"));

        mockMvc.perform(put(api("/configurations/SEUIL_PROROGATIONS"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("valeur", "12")))
                        .with(role("kc-dja-m15", "ROLE_DJA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeur").value("12"));

        for (String profil : List.of("ROLE_JURISTE", "ROLE_ASSISTANTE", "ROLE_SH")) {
            mockMvc.perform(put(api("/configurations/SEUIL_PROROGATIONS"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("valeur", "13")))
                            .with(role("kc-refus-" + profil, profil)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Q-108 : le seuil CHARGE_MAX_AVOCAT suit la même règle que les autres paramètres")
    void seuilChargeMaxAvocat_ouvertAuDjEtALaDja() throws Exception {
        mockMvc.perform(put(api("/alertes/seuils"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "CHARGE_MAX_AVOCAT", "seuil", 21)))
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeur").value("21"));

        mockMvc.perform(put(api("/alertes/seuils"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "CHARGE_MAX_AVOCAT", "seuil", 22)))
                        .with(role("kc-dja-m15", "ROLE_DJA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeur").value("22"));

        mockMvc.perform(put(api("/alertes/seuils"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "CHARGE_MAX_AVOCAT", "seuil", 23)))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE")))
                .andExpect(status().isForbidden());
    }

    /**
     * Le pendant du test précédent, et il compte autant : l'ouverture consentie à la DJA est <b>à
     * sens unique</b>. Le contrôle d'opportunité reste réservé à la DJA, sans quoi RG-INT-03
     * perdrait la séparation de ses deux étapes — le même profil préparerait l'avis puis voterait la
     * validation conjointe. Ce test empêche qu'on « harmonise » les deux rôles par symétrie.
     */
    @Test
    @DisplayName("Q-108 : le contrôle d'opportunité des frais reste refusé au DJ")
    void controleOpportunite_resteReserveALaDja() throws Exception {
        String corps = json(Map.of("accord", true));

        mockMvc.perform(put(api("/frais/demandes/999999/opportunite"))
                        .contentType(MediaType.APPLICATION_JSON).content(corps)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isForbidden());

        // La DJA franchit la garde : la demande est introuvable, ce qui n'est plus une question de droits.
        mockMvc.perform(put(api("/frais/demandes/999999/opportunite"))
                        .contentType(MediaType.APPLICATION_JSON).content(corps)
                        .with(role("kc-dja-m15", "ROLE_DJA")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    /**
     * Q-109 — la supervision du canal applicatif est une lecture d'administration : DJ et DJA, comme
     * l'écriture des paramètres. Elle expose un taux global, tous destinataires confondus ; l'ouvrir
     * plus largement reviendrait à publier l'activité de chacun.
     */
    @Test
    @DisplayName("Q-109 : le taux de délivrance des alertes est réservé au DJ et à la DJA")
    void statistiquesDiffusion_reserveesAuDjEtALaDja() throws Exception {
        for (String compte : List.of("kc-dj-m15", "kc-dja-m15")) {
            String profil = compte.contains("dja") ? "ROLE_DJA" : "ROLE_DJ";
            mockMvc.perform(get(api("/alertes/statistiques-diffusion"))
                            .param("dateDebut", "2026-09-01").param("dateFin", "2026-09-30")
                            .with(role(compte, profil)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.observees").exists())
                    .andExpect(jsonPath("$.tauxDelivrance").exists())
                    .andExpect(jsonPath("$.tauxDiffusionTempsReel").exists());
        }

        for (String profil : List.of("ROLE_JURISTE", "ROLE_ASSISTANTE", "ROLE_SH")) {
            mockMvc.perform(get(api("/alertes/statistiques-diffusion"))
                            .param("dateDebut", "2026-09-01").param("dateFin", "2026-09-30")
                            .with(role("kc-superv-" + profil, profil)))
                    .andExpect(status().isForbidden());
        }
    }

    /**
     * Une fenêtre inversée est une erreur de saisie, pas une fenêtre vide : la laisser passer
     * renverrait un taux de 0 % parfaitement silencieux, que l'on prendrait pour une panne.
     */
    @Test
    @DisplayName("Q-109 : une fenêtre inversée est refusée plutôt que renvoyée à zéro")
    void statistiquesDiffusion_refusentUneFenetreInversee() throws Exception {
        mockMvc.perform(get(api("/alertes/statistiques-diffusion"))
                        .param("dateDebut", "2026-09-30").param("dateFin", "2026-09-01")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isBadRequest());
    }


    // ---------------------------------------------------------------- Audit (Q-112)

    /**
     * Le test le plus important du lot : la consultation de l'audit est <b>elle-même historisée</b>.
     *
     * <p>Le filtre par personne étant autorisé, cet écran permet de suivre nommément l'activité d'un
     * salarié. La trace de son usage protège le DJ et la DJA autant que ceux qu'ils consultent.
     */
    @Test
    @DisplayName("Q-112 : consulter l'audit laisse une trace portant les critères employés")
    void audit_saPropreConsultationEstHistorisee() throws Exception {
        Long djaId = utilisateurRepository.findByKeycloakId("kc-dja-m15").orElseThrow().getId();

        mockMvc.perform(get(api("/historique")).param("utilisateurId", String.valueOf(djaId))
                        .param("perimetre", "TOUT").with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk());

        JsonNode trace = lireJson(mockMvc.perform(get(api("/historique"))
                        .param("code", "AUDIT_CONSULTE").param("size", "5")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(trace.get("totalElements").asLong()).isPositive();

        /*
         * La ligne la plus récente est **cette lecture-ci**, pas celle qu'on cherche : consulter
         * l'audit laisse une trace, qui prend aussitôt la tête du classement. Une première version
         * de ce test lisait content[0] et échouait pour cette raison — le défaut était dans le test,
         * et la propriété qu'il révèle mérite d'être affirmée plutôt que contournée.
         */
        JsonNode derniere = trace.get("content").get(0);
        assertThat(derniere.get("code").asText()).isEqualTo("AUDIT_CONSULTE");
        assertThat(derniere.get("details").get("codeRecherche").asText()).isEqualTo("AUDIT_CONSULTE");

        // Et parmi les traces, celle de la consultation par personne porte bien son critère — sous
        // un nom distinct de l'auteur de la consultation, que confondre rendrait la trace illisible.
        boolean recherchePersonneTracee = false;
        for (JsonNode entree : trace.get("content")) {
            if (entree.get("details").has("auteurRecherche")) recherchePersonneTracee = true;
        }
        assertThat(recherchePersonneTracee).as("la recherche par personne est tracée").isTrue();
    }

    @Test
    @DisplayName("Q-112 : l'audit couvre tout l'historique, dossiers compris, et reste réservé au DJ et à la DJA")
    void audit_couvreToutEtResteReserve() throws Exception {
        long dossierId = creerDossier();

        JsonNode tout = lireJson(mockMvc.perform(get(api("/historique")).param("size", "100")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(tout.get("totalElements").asLong()).isPositive();
        assertThat(tout.get("actionsDisponibles")).isNotEmpty();

        // Le périmètre d'un dossier ne renvoie que ses lignes — jamais les actions système.
        JsonNode duDossier = lireJson(mockMvc.perform(get(api("/historique"))
                        .param("perimetre", "UN_DOSSIER").param("dossierId", String.valueOf(dossierId))
                        .param("size", "100").with(role("kc-dja-m15", "ROLE_DJA")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(duDossier.get("content")).isNotEmpty();
        for (JsonNode entree : duDossier.get("content")) {
            assertThat(entree.get("code").asText()).isNotEqualTo("CONFIGURATION_MODIFIEE");
        }

        for (String profil : List.of("ROLE_JURISTE", "ROLE_ASSISTANTE", "ROLE_SH")) {
            mockMvc.perform(get(api("/historique")).with(role("kc-audit-" + profil, profil)))
                    .andExpect(status().isForbidden());
        }
    }

    /**
     * Un périmètre « un dossier » sans dossier renverrait tout l'historique : le pire des résultats,
     * puisqu'il ressemble à une réponse. Refusé plutôt qu'interprété.
     */
    @Test
    @DisplayName("Q-112 : les combinaisons de critères sans signification sont refusées")
    void audit_refuseLesCriteresIncoherents() throws Exception {
        mockMvc.perform(get(api("/historique")).param("perimetre", "UN_DOSSIER")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(api("/historique")).param("dateDebut", "2026-09-30").param("dateFin", "2026-09-01")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isBadRequest());

        // Un code inconnu est refusé par la conversion, là où une phrase libre renvoyait zéro ligne.
        mockMvc.perform(get(api("/historique")).param("code", "CODE_QUI_NEXISTE_PAS")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isBadRequest());
    }

    /** Q-112 : l'écran des paramètres répond sur place à « qui a mis ce réglage là ? ». */
    @Test
    @DisplayName("Q-112 : un paramètre porte l'auteur de sa dernière modification")
    void configuration_porteLAuteurDeLaDerniereModification() throws Exception {
        mockMvc.perform(put(api("/configurations/SEUIL_PROROGATIONS"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("valeur", "16")))
                        .with(role("kc-dja-m15", "ROLE_DJA")))
                .andExpect(status().isOk());
        Long djaId = utilisateurRepository.findByKeycloakId("kc-dja-m15").orElseThrow().getId();

        JsonNode configurations = lireJson(mockMvc.perform(get(api("/configurations"))
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode seuil = null;
        for (JsonNode c : configurations) {
            if ("SEUIL_PROROGATIONS".equals(c.get("cle").asText())) seuil = c;
        }
        assertThat(seuil).isNotNull();
        assertThat(seuil.get("dernierAuteurId").asLong()).isEqualTo(djaId);
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

    /** La reference attribuee par le serveur, que le client ne choisit plus (2026-09-29). */
    private String extraireReference(MvcResult resultat) throws Exception {
        JsonNode noeud = lireJson(resultat);
        assertThat(noeud.has("reference")).as("réponse sans référence : %s", noeud).isTrue();
        return noeud.get("reference").asText();
    }

    /** Ferme les étapes d'un dossier, condition du versement en jurisprudence (2026-09-29). */
    private void cloreLesEtapes(long dossierId) {
        etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossierId).forEach(etape -> {
            etape.setStatutCycleVie(com.afriland.audiences.dossier.domain.StatutCycleVie.CLOTURE);
            etapeProcedureRepository.save(etape);
        });
    }

    private long extraireId(MvcResult resultat) throws Exception {
        JsonNode noeud = lireJson(resultat);
        assertThat(noeud.has("id")).as("réponse sans identifiant : %s", noeud).isTrue();
        return noeud.get("id").asLong();
    }
}
