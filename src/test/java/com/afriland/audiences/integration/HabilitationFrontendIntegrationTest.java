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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M15: integration tests for the endpoints that make the backend usable by a frontend (form
 * reference data, stakeholder administration, work lists).
 *
 * <p>Their risk lies in routing, query filters and above all <b>authorizations</b>, which a mocked
 * service test cannot check. Hence a full Spring context on a real Flyway-migrated PostgreSQL, with
 * a simulated JWT and a mocked {@link MinioClient}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HabilitationFrontendIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
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

    /**
     * N4, full workflow over HTTP: the legal officer proposes, the DJ arbitrates, then the DJA withdraws.
     *
     * <p>A single method because it is a <b>chain</b>: each step depends on the previous state. It proves
     * the {@code @PreAuthorize} placement and that the threshold follows the decision: global until
     * validated, derogatory after, global again after withdrawal.
     */
    @Test
    @DisplayName("La diligence particulière se propose, s'arbitre et se retire après l'ouverture")
    void diligence_circuitCompletApresOuverture() throws Exception {
        long dossierId = creerDossierOrdinaire();

        // 1. The legal officer proposes. Nothing changes for fees before arbitration.
        JsonNode propose = lireJson(mockMvc.perform(post(api("/dossiers/" + dossierId + "/sensibilite/proposition"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"seuilMontant":60000000,"typeClientSensible":"VIP",
                                 "motif":"La contrepartie est devenue une personne exposée"}
                                """)
                        .with(role("kc-juriste-m15", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(propose.get("estSensible").asBoolean()).isTrue();
        assertThat(propose.get("sensibiliteStatut").asText()).isEqualTo("EN_ATTENTE");
        assertThat(propose.get("seuilOrigine").asText()).isEqualTo("GLOBAL");
        assertThat(propose.get("seuilMontant").isNull()).isTrue();

        // Proposing twice makes no sense: the refusal states which situation applies.
        mockMvc.perform(post(api("/dossiers/" + dossierId + "/sensibilite/proposition"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"seuilMontant":60000000,"typeClientSensible":"VIP","motif":"Encore"}
                                """)
                        .with(role("kc-juriste-m15", "ROLE_SAISIE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("déjà en attente")));

        // 2. The DJ arbitrates through the existing endpoint, unaware of where the proposal comes from.
        JsonNode valide = lireJson(mockMvc.perform(post(api("/dossiers/" + dossierId + "/sensibilite/validation"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"statut":"VALIDEE"}
                                """)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(valide.get("sensibiliteStatut").asText()).isEqualTo("VALIDEE");
        assertThat(valide.get("seuilMontant").asDouble()).isEqualTo(60_000_000d);
        assertThat(valide.get("seuilOrigine").asText()).isEqualTo("DEROGATION");

        // 3. Withdrawal requires a reason: removing sensitivity takes the case out of the fee workflow.
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/sensibilite"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"estSensible":false}
                                """)
                        .with(role("kc-dja-m15", "ROLE_DJA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("motif est obligatoire")));

        JsonNode retire = lireJson(mockMvc.perform(put(api("/dossiers/" + dossierId + "/sensibilite"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"estSensible":false,"motif":"L'affaire s'est révélée ordinaire"}
                                """)
                        .with(role("kc-dja-m15", "ROLE_DJA")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(retire.get("estSensible").asBoolean()).isFalse();
        assertThat(retire.get("sensibiliteMotifRetrait").asText()).isEqualTo("L'affaire s'est révélée ordinaire");
        // The threshold becomes global again: a derogation threshold only exists for a sensitive case.
        assertThat(retire.get("seuilOrigine").asText()).isEqualTo("GLOBAL");
        assertThat(retire.get("seuilMontant").isNull()).isTrue();

        // 4. Nothing to withdraw twice.
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/sensibilite"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"estSensible":false,"motif":"Encore"}
                                """)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("rien à retirer")));

        // 5. The case can become sensitive again: its past does not prevent it.
        mockMvc.perform(post(api("/dossiers/" + dossierId + "/sensibilite/proposition"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"seuilMontant":90000000,"typeClientSensible":"VIP","motif":"Le risque a doublé"}
                                """)
                        .with(role("kc-juriste-m15", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sensibiliteStatut").value("EN_ATTENTE"))
                // The withdrawal reason is cleared: it no longer applies to the current situation.
                .andExpect(jsonPath("$.sensibiliteMotifRetrait").doesNotExist());
    }

    /**
     * Role split: the legal officer <b>proposes</b>, the DJ and DJA <b>decide</b>. Setting sensitivity
     * triggers double fee validation, so it is not a data-entry action.
     */
    @Test
    @DisplayName("Le juriste propose la diligence, il ne la pose pas ; le DJ fait l'inverse")
    void diligence_partageDesRoles() throws Exception {
        long dossierId = creerDossierOrdinaire();

        // The legal officer cannot set it directly.
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/sensibilite"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"estSensible":true,"seuilMontant":60000000,"typeClientSensible":"VIP"}
                                """)
                        .with(role("kc-juriste-m15", "ROLE_SAISIE", "ROLE_JURISTE")))
                .andExpect(status().isForbidden());

        // The DJ sets it directly, without a proposal: they are the decision.
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/sensibilite"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"estSensible":true,"seuilMontant":60000000,"typeClientSensible":"VIP",
                                 "motif":"Décision du comité"}
                                """)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensibiliteStatut").value("VALIDEE"))
                .andExpect(jsonPath("$.seuilOrigine").value("DEROGATION"));

        // The Assistant can do neither.
        mockMvc.perform(post(api("/dossiers/" + dossierId + "/sensibilite/proposition"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {"seuilMontant":60000000,"typeClientSensible":"VIP","motif":"Motif"}
                                """)
                        .with(role("kc-assistante-m15", "ROLE_ASSISTANTE", "ROLE_CONSULTATION")))
                .andExpect(status().isForbidden());
    }

    /** An **ordinary** case without sensitivity: the N4 starting point. */
    private long creerDossierOrdinaire() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                        .content(json(Map.of("nom", "Client N4 " + n)))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m15").orElseThrow().getId();
        return creerDossierPourClient(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE"),
                clientId, juristeId, avocatId, "N4-" + n);
    }

    /**
     * N1: full lifecycle of an agency over HTTP, in one method because each step depends on the
     * identifier returned by the previous one (and codes are unique).
     *
     * <p>Proves creation, duplicate code refusal, code change refusal, closing, <b>reopening</b>, and that
     * a closed agency leaves the entry lists <b>without leaving the database</b>.
     */
    @Test
    @DisplayName("Une agence se crée, se corrige, se ferme et se rouvre ; son code ne change jamais")
    void agences_cycleDeVieComplet() throws Exception {
        String corps = """
                {"code":"00901","nom":"AGENCE INTEGRATION","ville":"Douala",
                 "adresse":"Rue du test","telephone":"650 228 936"}
                """;

        JsonNode creee = lireJson(mockMvc.perform(post(api("/referentiels/agences"))
                        .contentType(MediaType.APPLICATION_JSON).content(corps)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actif").value(true))
                .andReturn());
        long id = creee.get("id").asLong();

        // The code is unique: a duplicate is a 409, not a 500 from the SQL constraint.
        mockMvc.perform(post(api("/referentiels/agences"))
                        .contentType(MediaType.APPLICATION_JSON).content(corps)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isConflict());

        // The code cannot be changed: cases store it as plain text, without a foreign key.
        mockMvc.perform(put(api("/referentiels/agences/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"00902","nom":"AGENCE INTEGRATION","ville":"Douala"}
                                """)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ne se modifie pas")));

        // Everything else can be corrected, code unchanged.
        mockMvc.perform(put(api("/referentiels/agences/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"00901","nom":"AGENCE INTEGRATION 2","ville":"Yaoundé"}
                                """)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("AGENCE INTEGRATION 2"))
                .andExpect(jsonPath("$.ville").value("Yaoundé"));

        // Closing: the agency leaves the entry lists...
        mockMvc.perform(delete(api("/referentiels/agences/" + id))
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));

        assertThat(codesProposes()).doesNotContain("00901");
        // ...but not the database: the administration screen still sees it.
        assertThat(codesConnus()).contains("00901");

        // Reopening: same agency, same code, selectable again.
        mockMvc.perform(patch(api("/referentiels/agences/" + id + "/reactivation"))
                        .with(role("kc-dja-m15", "ROLE_DJA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.code").value("00901"));

        assertThat(codesProposes()).contains("00901");
    }

    /**
     * <b>Reading</b> is open to consultation (it explains what the form offers), but writing is reserved
     * to the DJ and DJA: an agency code affects every case that carries it.
     */
    @Test
    @DisplayName("Les agences se lisent avec CONSULTATION, ne s'écrivent qu'en DJ ou DJA")
    void agences_lectureOuverteEcritureFermee() throws Exception {
        mockMvc.perform(get(api("/referentiels/agences"))
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(post(api("/referentiels/agences"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"00903","nom":"INTERDITE","ville":"Douala"}
                                """)
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_CONSULTATION")))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(api("/referentiels/agences/1/reactivation"))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_CONSULTATION")))
                .andExpect(status().isForbidden());
    }

    /**
     * Search applies to <b>code</b> and <b>name</b>, by fragment and case-insensitive: "bona" must return
     * Bonanjo, Bonaberi, Bonamoussadi and Bonapriso, loaded by V42.
     */
    @Test
    @DisplayName("La recherche d'agence porte sur le code et sur le nom, sans distinction de casse")
    void agences_rechercheSurCodeEtNom() throws Exception {
        JsonNode parNom = lireJson(mockMvc.perform(get(api("/referentiels/agences"))
                        .param("recherche", "BONA").param("size", "100")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(parNom.get("totalElements").asInt()).isGreaterThan(1);
        parNom.get("content").forEach(agence ->
                assertThat(agence.get("nom").asText().toLowerCase()).contains("bona"));

        JsonNode parCode = lireJson(mockMvc.perform(get(api("/referentiels/agences"))
                        .param("recherche", "00002")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(parCode.get("totalElements").asInt()).isEqualTo(1);
        assertThat(parCode.get("content").get(0).get("nom").asText()).isEqualTo("BONANJO");

        // All 82 agencies of V42 have a city: it distinguishes homonyms.
        JsonNode toutes = lireJson(mockMvc.perform(get(api("/referentiels/agences"))
                        .param("size", "200")
                        .with(role("kc-juriste-m15", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());
        toutes.get("content").forEach(agence ->
                assertThat(agence.get("ville").asText()).isNotBlank());
    }

    /** Codes offered for <b>data entry</b>: open agencies only. */
    private List<String> codesProposes() throws Exception {
        return codes(true);
    }

    /** Codes seen by <b>administration</b>: open and closed. */
    private List<String> codesConnus() throws Exception {
        return codes(false);
    }

    private List<String> codes(boolean actifSeulement) throws Exception {
        JsonNode page = lireJson(mockMvc.perform(get(api("/referentiels/agences"))
                        .param("actifSeulement", String.valueOf(actifSeulement))
                        .param("size", "300")
                        .with(role("kc-dj-m15", "ROLE_DJ", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andReturn());
        List<String> resultat = new java.util.ArrayList<>();
        page.get("content").forEach(agence -> resultat.add(agence.get("code").asText()));
        return resultat;
    }

    /**
     * Replays the full chain: the frontend lists legal officers, keeps an identifier and uses it to
     * create a case. Without {@code GET /utilisateurs}, that last call could not be built.
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
                                // Mandatory.
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))),
                                // Mandatory: 00002 = Bonanjo.
                                Map.entry("codeAgenceOrigine", "00002"))))
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

        // The AVOCAT profile does not carry ROLE_CONSULTATION (see audiences-realm.json).
        mockMvc.perform(get(api("/utilisateurs")).with(role("kc-avocat-externe", "ROLE_AVOCAT")))
                .andExpect(status().isForbidden());
    }

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
        // The response is paginated: `{content, totalPages, totalElements}`.
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

    @Test
    @DisplayName("Chaque file de travail est servie à son profil et refusée aux autres")
    void filesDeTravail_respectentLesHabilitations() throws Exception {
        // Publications awaiting validation: Assistant
        mockMvc.perform(get(api("/publications")).param("statut", "DEPOSE")
                        .with(role("kc-assistante-m15", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get(api("/publications")).with(role("kc-sh-m15", "ROLE_SH")))
                .andExpect(status().isForbidden());

        // Deletion requests: DJ/DJA
        mockMvc.perform(get(api("/ged/demandes-suppression")).param("statut", "EN_ATTENTE")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists());
        mockMvc.perform(get(api("/ged/demandes-suppression"))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE")))
                .andExpect(status().isForbidden());

        // Retainer letters to sign: SH
        mockMvc.perform(get(api("/constitutions/prestataires")).param("statut", "EN_ATTENTE")
                        .with(role("kc-sh-m15", "ROLE_SH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists());
        /*
         * The Assistant has access too: she requests and drafts like a legal officer. The letter she
         * creates is hers (only she writes it), and the line manager corrects it before signing.
         */
        mockMvc.perform(get(api("/constitutions/prestataires"))
                        .with(role("kc-assistante-m15", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk());
        // She drafts, she does not sign: the second signature stays with the line manager.
        mockMvc.perform(put(api("/constitutions/prestataires/1/validation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"decision\":\"VALIDEE\"}")
                        .with(role("kc-assistante-m15", "ROLE_ASSISTANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Les onglets audiences / délibérés / alarmes d'un dossier sont servis, 404 si le dossier n'existe pas")
    void sousRessourcesDossier() throws Exception {
        long dossierId = creerDossier();

        /*
         * Hearings and alarms are paginated (they grow over the case's life). Deliberations stay a list:
         * at most one per step, three in total. This asymmetry is intentional and locked here.
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

    /**
     * R-06 scenario over HTTP: {@code GET /alertes/mes-notifications} is served by a
     * {@code @Transactional(readOnly = true)} service and resolves the current user. A new user's very
     * first request used to fail with a 500 ("cannot execute INSERT in a read-only transaction").
     */
    @Test
    @DisplayName("R-06 corrigé : un utilisateur inconnu est provisionné depuis un endpoint en lecture seule")
    void utilisateurInconnu_estProvisionneSurUnEndpointDeLecture() throws Exception {
        String nouveau = "kc-nouveau-m15-" + COMPTEUR.incrementAndGet();
        assertThat(utilisateurRepository.findByKeycloakId(nouveau)).isEmpty();

        mockMvc.perform(get(api("/alertes/mes-notifications")).with(role(nouveau, "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // The token-derived profile is checked by ProvisionnementLectureSeuleIntegrationTest; here only
        // the row's presence matters, proving the INSERT happened.
        assertThat(utilisateurRepository.findByKeycloakId(nouveau)).isPresent();
    }

    /**
     * {@code GET /alertes/mes-notifications} must have a deterministic {@code ORDER BY}, otherwise an
     * alert may appear twice or vanish across pages. The three alerts here are created in the same
     * second: only the {@code id} tie-breaker orders them.
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
     * {@code GET /clients} and {@code GET /dossiers} must paginate with an {@code ORDER BY}; otherwise,
     * past twenty clients, a newly created one could land on page 2, invisible to its author.
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

    /**
     * {@code PUT /clients/{id}}: same guard as creation, the reference stays fixed even if the body
     * carries another, and the change leaves a readable audit trace.
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

        // The lawyer lacks ROLE_SAISIE: they do not edit a bank client.
        mockMvc.perform(put(api("/clients/" + clientId))
                        .with(role("kc-avocat-m15", "ROLE_AVOCAT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Intrusion"))))
                .andExpect(status().isForbidden());

        // The body carries another reference: it is ignored, the identifier does not move.
        mockMvc.perform(put(api("/clients/" + clientId)).with(juriste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reference", "AUTRE-" + reference, "nom", "Nom corrigé",
                                "email", "contact@client.cm"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.nom").value("Nom corrigé"))
                .andExpect(jsonPath("$.email").value("contact@client.cm"));

        // An empty name is refused, as on creation.
        mockMvc.perform(put(api("/clients/" + clientId)).with(juriste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", " "))))
                .andExpect(status().isBadRequest());

        // The trace: outside any case, with the reference and both name values.
        mockMvc.perform(get(api("/historique")).param("perimetre", "HORS_DOSSIER")
                        .param("code", "CLIENT_MODIFIE").param("size", "1")
                        .with(role("kc-dj-m15", "ROLE_DJ", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].details.reference").value(reference))
                .andExpect(jsonPath("$.content[0].details.ancienNom").value("Nom avec coquile"))
                .andExpect(jsonPath("$.content[0].details.nouveauNom").value("Nom corrigé"))
                .andExpect(jsonPath("$.content[0].details.nouvelEmail").value("contact@client.cm"));
    }

    /**
     * {@code PUT /intervenants/{id}}: same guard as creation (DJ, DJA); neither the type nor the Keycloak
     * identifier changes, even if the body carries others (identity is managed in Keycloak). Name and
     * contact details can be corrected, and the change is traced.
     */
    @Test
    @DisplayName("Q-117 : un avocat se corrige, son type et son identifiant Keycloak restent figés, et c'est tracé")
    void intervenant_seModifie_typeEtCompteFiges_etTrace() throws Exception {
        RequestPostProcessor dj = role("kc-dj-m15", "ROLE_DJ", "ROLE_CONSULTATION", "ROLE_SAISIE");
        long n = COMPTEUR.incrementAndGet();
        String compte = "kc-q117-" + n;
        long avocatDuTestId = extraireId(mockMvc.perform(post(api("/intervenants")).with(dj)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", "Me Coquile " + n, "compteKeycloak", compte))))
                .andExpect(status().isCreated()).andReturn());
        // The legal officer reads the reference data but does not modify it.
        mockMvc.perform(put(api("/intervenants/" + avocatDuTestId))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE", "ROLE_CONSULTATION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Intrusion"))))
                .andExpect(status().isForbidden());

        // The body carries another type and account: they are ignored.
        mockMvc.perform(put(api("/intervenants/" + avocatDuTestId)).with(dj).contentType(MediaType.APPLICATION_JSON)
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
                .andExpect(jsonPath("$.content[0].details.prestataireId").value(avocatDuTestId))
                .andExpect(jsonPath("$.content[0].details.nouveauNom").value("Me Corrigé " + n))
                .andExpect(jsonPath("$.content[0].details.nouvelEmail").value("corrige@cab.cm"))
                .andExpect(jsonPath("$.content[0].details.nouveauCompteKeycloak").doesNotExist());
    }

    /**
     * Steps, legal officers and lawyers of the consolidated view are loaded in three queries for the
     * whole list instead of three per case. The risk is mismatching; this checks two cases of the same
     * client assigned to different lawyers.
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
                        // RG-INT-01: a lawyer must have an account.
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
            // Every case is born with its INSTANCE step: the list must be neither empty nor duplicated.
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
                                // Mandatory.
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur " + reference))),
                                // Mandatory: 00002 = Bonanjo.
                                Map.entry("codeAgenceOrigine", "00002")))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    /**
     * Lawyer portal: what matters is what the lawyer must <b>not</b> see. Two cases are created (one
     * assigned to the lawyer, one not), and the second must appear neither in the list nor by its id.
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

        // The response is limited to identification: no client, no risk, no sensitivity.
        JsonNode premier = mesDossiers.get("content").get(0);
        assertThat(premier.has("reference")).isTrue();
        assertThat(premier.has("clientId")).as("le portefeuille de la banque ne doit pas fuiter").isFalse();
        assertThat(premier.has("risqueEncouru")).isFalse();
        assertThat(premier.has("estSensible")).isFalse();

        // Hearings of the lawyer's case are open, others are not (403, not 404).
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
     * UC-INT-01 step 4: the lawyer <b>uploads</b> supporting documents ({@code pieces_justificatives}).
     * Covers upload through review by the instructor (UC-INT-02 step 3).
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

        // The Assistant instructs: she must see the document, not just read its name (UC-INT-02 step 3).
        JsonNode detail = lireJson(mockMvc.perform(get(api("/frais/demandes/" + demandeId))
                        .with(role("kc-assistante-m15", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.get("piecesTelechargeables")).hasSize(1);
        assertThat(detail.get("piecesTelechargeables").get(0).get("nomFichier").asText()).isEqualTo("facture.pdf");
        // The field is set; its value comes from MinIO, whose client is mocked here.
        assertThat(detail.get("piecesTelechargeables").get(0).has("urlTelechargement")).isTrue();
    }

    /** A format outside FORMATS_GED is refused: same rules as the GED, one single setting. */
    @Test
    @DisplayName("Q-97 : une pièce d'un format non autorisé est refusée")
    void pieceJustificative_refuseUnFormatNonAutorise() throws Exception {
        mockMvc.perform(multipart(api("/avocat/pieces-justificatives"))
                        .file(new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] {1}))
                        .with(role("kc-avocat-m15", "ROLE_AVOCAT")))
                .andExpect(status().isBadRequest());
    }

    /** A case of the same client, assigned to another lawyer: the control for the restriction. */
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
                                // Mandatory.
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur " + n))),
                                // Mandatory: 00002 = Bonanjo.
                                Map.entry("codeAgenceOrigine", "00002"))))
                        .with(role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());
    }

    /**
     * Pagination: **ten rows per page, the eleventh on the next page**. Checks that the eleventh
     * **exists**, is **on page 2**, and **differs** from those of page 1: no data loss.
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

    /** Filter: the queue of sensitivity flags to validate. */
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

    /** Search-as-you-type of the selectors. */
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

        // Fragment in the middle of the name: what a B-tree index cannot serve.
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
                                // Mandatory.
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))),
                                // Mandatory: 00002 = Bonanjo.
                                Map.entry("codeAgenceOrigine", "00002"))))
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

    /** See {@code ParcoursEndToEndIntegrationTest#role}: authorities are provided explicitly. */
    @Test
    @DisplayName("Q-84 : un avocat est reconnu par son identifiant de connexion, et l'utilisateur le garde")
    void avocat_estReconnuParSonIdentifiantDeConnexion() throws Exception {
        String identifiant = "avocat.q84." + COMPTEUR.incrementAndGet();
        avocatRepository.save(Avocat.builder().nom("Me Q84").compteKeycloak(identifiant).build());

        mockMvc.perform(get(api("/frais/demandes")).with(avocatConnecte("kc-sub-" + identifiant, identifiant)))
                .andExpect(status().isOk());
        assertThat(utilisateurRepository.findByKeycloakId("kc-sub-" + identifiant))
                .get().extracting(Utilisateur::getIdentifiant).isEqualTo(identifiant);

        // An account with no linked lawyer, by identifier or by sub, stays refused.
        mockMvc.perform(get(api("/frais/demandes")).with(avocatConnecte("kc-sub-inconnu-" + identifiant, "inconnu." + identifiant)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Q-88 : les décisions d'un dossier se relisent, statut de paiement compris ; 404 si le dossier est inconnu")
    void decisionsDuDossier_seRelisent() throws Exception {
        long dossierId = creerDossier();
        RequestPostProcessor juriste = role("kc-juriste-m15", "ROLE_JURISTE", "ROLE_CONSULTATION");
        RequestPostProcessor assistante = role("kc-assistante-m15", "ROLE_ASSISTANTE", "ROLE_CONSULTATION");

        // A conviction is followed up on a closed step, like an adjudication.
        cloreLesEtapes(dossierId);
        long etapeId = etapeProcedureRepository.findByDossierIdOrderByTypeAsc(dossierId).get(0).getId();
        long condamnationId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/condamnations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sens", "TIERS_REDEVABLE", "naturePaiement", "Dommages et intérêts",
                                "dateDecision", LocalDate.now().toString(), "etapeId", etapeId)))
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
         * A decision joins the case-law base only once final (no step of the case in progress). This test
         * is about search, not lifecycle, so steps are closed directly.
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

        // A lowercase keyword fragment is enough.
        mockMvc.perform(get(api("/jurisprudences")).param("motCle", marque.toLowerCase()).with(consultation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        // Lowercase court, restricted to the case.
        mockMvc.perform(get(api("/jurisprudences")).param("motCle", marque).param("juridiction", "tgi douala")
                        .param("dossierId", String.valueOf(dossierId)).with(consultation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        // Another case does not contain it.
        mockMvc.perform(get(api("/jurisprudences")).param("motCle", marque).param("dossierId", "999999").with(consultation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** Lawyer token as issued by Keycloak: with its login identifier. */
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
        RequestPostProcessor juriste = role("kc-juriste-qf21", "ROLE_JURISTE", "ROLE_SAISIE");

        String compte = "kc-avocat-qf21-" + n;
        mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", "Me QF21 " + n,
                                "email", compte + "@cabinet.cm", "compteKeycloak", compte)))
                        .with(role("kc-dj-qf21-" + n, "ROLE_DJ")))
                .andExpect(status().isCreated());

        /*
         * ERR-CONFLICT covers a dozen unrelated conflicts. Without a "champ", a frontend had to guess the
         * target from the endpoint called. A lawyer's Keycloak account is still entered by hand
         * (RG-INT-01), which makes it a valid case for this.
         */
        mockMvc.perform(post(api("/intervenants"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AVOCAT", "nom", "Doublon QF21",
                                "email", "doublon-qf21-" + n + "@cabinet.cm", "compteKeycloak", compte)))
                        .with(role("kc-dj-qf21-" + n, "ROLE_DJ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR-CONFLICT"))
                .andExpect(jsonPath("$.champ").value("compteKeycloak"));

        // An error targeting no field does not invent one: the key is absent from the body, not null,
        // which keeps the addition backward compatible.
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
        // An account exists in the database only after its first authenticated call (R-06):
        // creating a client is not enough, a read going through getCurrentUser is needed.
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(juriste)).andExpect(status().isOk());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m16").orElseThrow().getId();
        long avocatDuTestId = avocatRepository.save(Avocat.builder().nom("Me M16 " + n)
                .compteKeycloak("kc-avocat-m16-nature-" + n).build()).getId();

        /*
         * 71 characters: beyond the former 50-character limit of `dossier.nature`, although this label
         * comes from the reference data the service queries.
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
                                Map.entry("avocatsAffectes", List.of(avocatDuTestId)),
                                // Mandatory.
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))),
                                // Mandatory: 00002 = Bonanjo.
                                Map.entry("codeAgenceOrigine", "00002"))))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nature").value(natureLongue))
                // The category was not submitted: it is derived from the nature (RG-DOS-02).
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
        // An account exists in the database only after its first authenticated call (R-06):
        // creating a client is not enough, a read going through getCurrentUser is needed.
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(juriste)).andExpect(status().isOk());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m16b").orElseThrow().getId();
        long avocatDuTestId = avocatRepository.save(Avocat.builder().nom("Me M16b " + n)
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
                                Map.entry("avocatsAffectes", List.of(avocatDuTestId)),
                                // Mandatory.
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))),
                                // Mandatory: 00002 = Bonanjo.
                                Map.entry("codeAgenceOrigine", "00002"))))
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
        // An account exists in the database only after its first authenticated call (R-06):
        // creating a client is not enough, a read going through getCurrentUser is needed.
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(juriste)).andExpect(status().isOk());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste-m16c").orElseThrow().getId();
        long avocatDuTestId = avocatRepository.save(Avocat.builder().nom("Me M16c " + n)
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
                                Map.entry("avocatsAffectes", List.of(avocatDuTestId)),
                                // Mandatory.
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))),
                                // Mandatory: 00002 = Bonanjo.
                                Map.entry("codeAgenceOrigine", "00002"))))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());

        /*
         * Reference: partial search, a reference is never remembered in full. It is assigned by the
         * system, so the test searches a fragment of the received reference (year and number, no prefix).
         */
        mockMvc.perform(get(api("/dossiers")).param("reference", reference.substring(4)).with(juriste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reference").value(reference));

        // Client: all cases brought against them.
        mockMvc.perform(get(api("/dossiers")).param("clientId", String.valueOf(clientId)).with(juriste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        /*
         * "My cases" resolves on the authenticated user. The same call by another legal officer must
         * return nothing: the filter cannot be used to browse a colleague's portfolio.
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
        long avocatDuTestId = avocatRepository.save(Avocat.builder().nom("Me Q74 " + n)
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
                                Map.entry("avocatsAffectes", List.of(avocatDuTestId)),
                                // Mandatory.
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Défendeur au procès"))),
                                // Mandatory: 00002 = Bonanjo.
                                Map.entry("codeAgenceOrigine", "00002"))))
                        .with(juriste))
                .andExpect(status().isCreated())
                .andReturn());

        // Before validation the only request is the initial proposal: readable, but not arbitrable as a
        // derogation. It is settled by the sensitivity validation.
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

        // The DJ reads the arbitration queue.
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

        // The endpoint replaces the lists: an empty list would remove everyone, violating RG-DOS-03.
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/affectation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("juristesAffectes", List.of(), "avocatsAffectes", List.of(avocatId))))
                        .with(juriste))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-VALIDATION"))
                .andExpect(jsonPath("$.details").value(containsString("juristesAffectes")));

        // A missing list counts as an empty list: same refusal.
        mockMvc.perform(put(api("/dossiers/" + dossierId + "/affectation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("juristesAffectes", List.of(juristeId))))
                        .with(juriste))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value(containsString("avocatsAffectes")));

        // The nominal case still passes, including duplicate confirmation.
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

        // The reason is mandatory, and cancellation is reserved to the legal officer, like scheduling.
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

        // An already cancelled hearing cannot be cancelled again.
        mockMvc.perform(patch(api("/audiences/" + audienceId + "/annulation"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("motif", "Encore"))).with(juriste))
                .andExpect(status().isBadRequest());

        /*
         * Rescheduling on the same date after a postponement is not a duplicate: direct 201, without
         * ?forcer. Otherwise the cancelled hearing would trigger ERR-005 and an alert to the DJ.
         */
        long nouvelleId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/audiences"))
                        .contentType(MediaType.APPLICATION_JSON).content(planification).with(juriste))
                .andExpect(status().isCreated())
                .andReturn());

        // The calendar (and its exports) excludes the cancelled hearing and keeps the rescheduled one:
        // the filter does not just empty everything.
        JsonNode calendrier = lireJson(mockMvc.perform(get(api("/audiences/calendrier"))
                        .param("periode", "TRIMESTRIELLE").param("dateDebut", LocalDate.now().toString())
                        .with(juriste))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(calendrier.findValues("audienceId").stream().map(JsonNode::asLong).toList())
                .contains(nouvelleId)
                .doesNotContain(audienceId);

        // A cancelled hearing gets no report: clean refusal, even before the date rule.
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

        // Unlike rescheduling, no replacement alarm was created.
        mockMvc.perform(get(api("/dossiers/" + dossierId + "/alarmes")).with(juriste))
                .andExpect(status().isOk())
                // Paginated: `$.length()` would count the three keys of the envelope.
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Moves the case's first step to "in progress": the precondition of a hearing (RG-AUD-01). */
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

        // The authoring legal officer requests (202): the request already names its file and case.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(api("/ged/documents/" + documentId)).with(juriste))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentNom").value(nom))
                .andExpect(jsonPath("$.dossierId").value(dossierId));

        // The DJ deletes the same document directly (200).
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(api("/ged/documents/" + documentId)).with(dj))
                .andExpect(status().isOk());

        // No more ghost pending request: it is closed with the document, validated by the DJ.
        JsonNode enAttente = lireJson(mockMvc.perform(get(api("/ged/demandes-suppression"))
                        .param("statut", "EN_ATTENTE").with(dj))
                .andExpect(status().isOk())
                .andReturn());
        // An empty queue is a valid outcome here: only the absence of this document matters.
        assertThat(enAttente.findValues("documentId").stream().map(JsonNode::asLong).toList())
                .noneMatch(id -> id.equals(documentId));

        /*
         * Explicit `size`: the queue is paginated and this test looks for **one** specific request in the
         * whole history. Relying on the default page would depend on how many requests other tests create.
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
        // The reference is assigned by the system: the test checks its format.
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

    /**
     * Writing system parameters is open to the DJ <b>and</b> the DJA, for continuity of service when the
     * DJ is absent. These endpoints previously had no authorization test.
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
     * The counterpart: opening to the DJA is <b>one-way</b>. The opportunity review stays reserved to the
     * DJA, otherwise RG-INT-03 would lose the separation of its two steps (the same profile would prepare
     * the opinion then vote the joint validation). Prevents "harmonizing" both roles by symmetry.
     */
    @Test
    @DisplayName("Q-108 : le contrôle d'opportunité des frais reste refusé au DJ")
    void controleOpportunite_resteReserveALaDja() throws Exception {
        String corps = json(Map.of("accord", true));

        mockMvc.perform(put(api("/frais/demandes/999999/opportunite"))
                        .contentType(MediaType.APPLICATION_JSON).content(corps)
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isForbidden());

        // The DJA passes the guard: the request is not found, which is no longer an authorization matter.
        mockMvc.perform(put(api("/frais/demandes/999999/opportunite"))
                        .contentType(MediaType.APPLICATION_JSON).content(corps)
                        .with(role("kc-dja-m15", "ROLE_DJA")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    /**
     * Supervision of the in-app channel is an administration read: DJ and DJA only. It exposes a global
     * rate across all recipients; opening it wider would publish everyone's activity.
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
     * An inverted window is an input error, not an empty window: letting it through would return a
     * silent 0 % rate, easily mistaken for an outage.
     */
    @Test
    @DisplayName("Q-109 : une fenêtre inversée est refusée plutôt que renvoyée à zéro")
    void statistiquesDiffusion_refusentUneFenetreInversee() throws Exception {
        mockMvc.perform(get(api("/alertes/statistiques-diffusion"))
                        .param("dateDebut", "2026-09-30").param("dateFin", "2026-09-01")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isBadRequest());
    }


    /**
     * Consulting the audit is <b>itself logged</b>. Since filtering by person is allowed, this screen can
     * track an employee's activity by name; tracing its use protects both sides.
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
         * The most recent row is **this read itself**: consulting the audit leaves a trace, which immediately
         * tops the ranking. The property is asserted rather than worked around.
         */
        JsonNode derniere = trace.get("content").get(0);
        assertThat(derniere.get("code").asText()).isEqualTo("AUDIT_CONSULTE");
        assertThat(derniere.get("details").get("codeRecherche").asText()).isEqualTo("AUDIT_CONSULTE");

        // Among the traces, the per-person consultation carries its criterion, under a name distinct from
        // the consultation's author (mixing them would make the trace unreadable).
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

        // A case scope returns only its rows, never system actions.
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
     * A "single case" scope without a case would return the whole history, which looks like an answer.
     * Refused rather than interpreted.
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

        // An unknown code is refused by conversion, where free text would return zero rows.
        mockMvc.perform(get(api("/historique")).param("code", "CODE_QUI_NEXISTE_PAS")
                        .with(role("kc-dj-m15", "ROLE_DJ")))
                .andExpect(status().isBadRequest());
    }

    /** The parameters screen answers "who set this value?" in place. */
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

    /**
     * Response body read as <b>UTF-8</b>. {@code getContentAsString()} without argument decodes as
     * ISO-8859-1 when no charset is declared, which double-encodes accented characters.
     */
    private JsonNode lireJson(MvcResult resultat) throws Exception {
        return objectMapper.readTree(resultat.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** The reference assigned by the server, which the client no longer chooses. */
    private String extraireReference(MvcResult resultat) throws Exception {
        JsonNode noeud = lireJson(resultat);
        assertThat(noeud.has("reference")).as("réponse sans référence : %s", noeud).isTrue();
        return noeud.get("reference").asText();
    }

    /** Closes the case's steps, the precondition for adding it to case law. */
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
