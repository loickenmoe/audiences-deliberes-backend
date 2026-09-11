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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M14 — tests d'intégration de bout en bout des 9 parcours utilisateurs de FUNCTIONAL_MAP.md §9.
 * <p>
 * Contexte Spring complet (sécurité incluse) + PostgreSQL réel migré par Flyway (Testcontainers),
 * appels HTTP réels via {@link MockMvc}. Deux substitutions assumées, documentées en Q-65 :
 * <ul>
 *   <li>l'authentification passe par un JWT simulé ({@code spring-security-test}) plutôt qu'un
 *       Keycloak conteneurisé — les claims produits sont identiques à ceux du vrai jeton
 *       ({@code sub}, {@code roles}, {@code given_name}…), seule la vérification cryptographique
 *       est court-circuitée ;</li>
 *   <li>{@link MinioClient} est mocké — le stockage objet lui-même est hors périmètre de ces
 *       tests, il a été vérifié en conditions réelles à chaque jalon (M8 à M12).</li>
 * </ul>
 * Ces tests valident le <b>câblage complet</b> (routes, sécurité, transactions, interactions
 * inter-modules, persistance réelle), pas les règles métier unitaires — déjà couvertes par les
 * 179 tests de service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ParcoursEndToEndIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("audiences_e2e")
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
    @Autowired private com.afriland.audiences.audience.repository.AudienceRepository audienceRepository;

    private Long avocatId;

    @BeforeEach
    void setUp() {
        // Les utilisateurs sont pré-provisionnés pour que les parcours restent déterministes
        // (identifiants et profils connus d'avance). R-06 — provisionnement au premier appel
        // incompatible avec un endpoint @Transactional(readOnly = true) — a été corrigé en M15
        // (UtilisateurProvisioningService, propagation REQUIRES_NEW) et est couvert par
        // ProvisionnementLectureSeuleIntegrationTest.
        provisionner("kc-juriste", "JURISTE");
        provisionner("kc-dj", "DJ");
        provisionner("kc-dja", "DJA");
        provisionner("kc-assistante", "ASSISTANTE");
        provisionner("kc-sh", "SH");
        provisionner("kc-avocat", "AVOCAT");
        avocatId = avocatRepository.findByCompteKeycloak("kc-avocat")
                .map(Avocat::getId)
                .orElseGet(() -> avocatRepository.save(Avocat.builder()
                        .nom("Me Test").email("avocat@test.cm").compteKeycloak("kc-avocat").build()).getId());
    }

    // ---------------------------------------------------------------- P1

    @Test
    @DisplayName("P1 — Ouverture d'un dossier de recouvrement : création, affectations, pièce GED, étape EN_COURS")
    void p1_ouvertureDossierRecouvrement() throws Exception {
        long dossierId = creerDossierRecouvrement();

        // Chargement d'une pièce en GED par le juriste
        MockMultipartFile fichier = new MockMultipartFile("file", "piece.pdf", "application/pdf", new byte[]{1, 2, 3});
        mockMvc.perform(multipart(api("/ged/documents"))
                        .file(fichier)
                        .param("dossierId", String.valueOf(dossierId))
                        .param("typeDocument", "Pièce de procédure")
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated());

        // Étape INSTANCE : OUVERTURE → EN_COURS
        long etapeId = premiereEtape(dossierId);
        mockMvc.perform(patch(api("/dossiers/" + dossierId + "/etapes/" + etapeId + "/statut"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nouveauStatut", "EN_COURS")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCycleVie").value("EN_COURS"));

        // Le dossier consolidé expose les affectations et l'historique. Le champ `documents` de
        // `DossierResponse` reste vide **par construction** : le module `dossier` ne peut pas
        // dépendre de `ged` (inversion de couches) — les pièces sont servies par l'endpoint dédié
        // `GET /dossiers/{id}/documents` (#43, M8). Cf. Q-67.
        mockMvc.perform(get(api("/dossiers/" + dossierId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(0))
                .andExpect(jsonPath("$.historique.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.avocatsAffectes.length()").value(1));

        mockMvc.perform(get(api("/dossiers/" + dossierId + "/documents")).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------------------------------------------------------------- P2

    @Test
    @DisplayName("P2 — Cycle d'une audience : planification, compte rendu juriste, CR avocat validé par l'Assistante")
    void p2_cycleAudience() throws Exception {
        long dossierId = creerDossierRecouvrement();
        long etapeId = premiereEtape(dossierId);
        passerEtape(dossierId, etapeId, "EN_COURS");

        // RG-AUD-07 : la planification exige une date future…
        long audienceId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/audiences"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "datePlanifiee", LocalDate.now().plusDays(7).toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andReturn());

        // …tandis que RG-AUD-01 exige que l'audience soit passée pour saisir son compte rendu :
        // le temps est simulé en antidatant la ligne, seul moyen de dérouler le parcours complet
        // dans un même test (le refus sur audience future est déjà couvert unitairement).
        audienceRepository.findById(audienceId).ifPresent(audience -> {
            audience.setDatePlanifiee(LocalDate.now().minusDays(1));
            audienceRepository.save(audience);
        });

        // Compte rendu interne du juriste (Épic 2)
        mockMvc.perform(put(api("/audiences/" + audienceId + "/compte-rendu"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("compteRendu", "Audience tenue, affaire mise en délibéré")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TENUE"));

        // Compte rendu publié par l'avocat (Épic 5), validé par l'Assistante
        long publicationId = extraireId(mockMvc.perform(post(api("/publications/cr-audience"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dossierId", dossierId, "audienceId", audienceId,
                                "contenu", "Plaidoirie effectuée, renvoi au délibéré")))
                        .with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(put(api("/publications/" + publicationId + "/validation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("statut", "VALIDE")))
                        .with(role("kc-assistante", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutValidation").value("VALIDE"));

        // Une fois validée, la publication devient consultable
        mockMvc.perform(get(api("/publications/" + publicationId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- P3

    @Test
    @DisplayName("P3 — Cycle d'un délibéré (Q-78) : mise en délibéré, prorogation, rabattement, nouvelle mise en délibéré, vidage")
    void p3_cycleDelibere() throws Exception {
        long dossierId = creerDossierRecouvrement();
        long etapeId = premiereEtape(dossierId);
        passerEtape(dossierId, etapeId, "EN_COURS");
        passerEtape(dossierId, etapeId, "EN_DELIBERE");
        LocalDate annoncee = LocalDate.now().plusDays(7);
        LocalDate prorogee = LocalDate.now().plusDays(14);

        // Mise en délibéré : pas de résultat, l'étape reste EN_DELIBERE.
        long premier = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/deliberes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "dateDeliberee", annoncee.toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE"))
                .andReturn());

        // Un seul délibéré en attente par étape.
        mockMvc.perform(post(api("/dossiers/" + dossierId + "/deliberes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "dateDeliberee", annoncee.toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isConflict());

        // « DÉLIBÉRÉ PROROGÉ AU [date] » : pendant l'attente, date future, qui devient la date annoncée.
        mockMvc.perform(post(api("/deliberes/" + premier + "/prorogations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", prorogee.toString(), "motif", "Juge empêché")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compteur").value(1))
                .andExpect(jsonPath("$.alerte").value(false));

        mockMvc.perform(get(api("/deliberes/" + premier + "/prorogations")).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(prorogee.toString()));

        // « DÉLIBÉRÉ RABATTU LE [date, motif] » : le juge rouvre les débats avant de statuer.
        mockMvc.perform(post(api("/deliberes/" + premier + "/rabattement"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", LocalDate.now().toString(), "motif", "Réouverture des débats")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutCycleVie").value("EN_COURS"));

        // Un délibéré rabattu ne se proroge plus.
        mockMvc.perform(post(api("/deliberes/" + premier + "/prorogations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", prorogee.toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isBadRequest());

        // Nouvelle mise en délibéré, puis la décision est rendue : le délibéré est vidé.
        passerEtape(dossierId, etapeId, "EN_DELIBERE");
        long second = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/deliberes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "dateDeliberee", LocalDate.now().plusDays(21).toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(put(api("/deliberes/" + second + "/resultat"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("resultat", "FAVORABLE")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("VIDE"))
                .andExpect(jsonPath("$.statutExpedition").value("A_LEVER"));

        // Après la décision, ni prorogation ni rabattement.
        mockMvc.perform(post(api("/deliberes/" + second + "/rabattement"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", LocalDate.now().toString(), "motif", "Trop tard")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isBadRequest());

        // L'onglet du dossier retrace les deux passages : le vidé, puis le rabattu avec son motif.
        mockMvc.perform(get(api("/dossiers/" + dossierId + "/deliberes")).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etat").value("VIDE"))
                .andExpect(jsonPath("$[1].etat").value("RABATTU"))
                .andExpect(jsonPath("$[1].motifRabattement").value("Réouverture des débats"))
                .andExpect(jsonPath("$[1].nombreProrogations").value(1));
    }

    // ---------------------------------------------------------------- P4

    @Test
    @DisplayName("P4 — Voie de recours : délibéré défavorable, échéance de recours consultable, clôture")
    void p4_voieDeRecours() throws Exception {
        long dossierId = creerDossierRecouvrement();
        long etapeId = premiereEtape(dossierId);
        passerEtape(dossierId, etapeId, "EN_COURS");
        passerEtape(dossierId, etapeId, "EN_DELIBERE");

        long delibereId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/deliberes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "dateDeliberee", LocalDate.now().toString(),
                                "resultat", "DEFAVORABLE",
                                "dateEcheanceRecours", LocalDate.now().plusDays(30).atStartOfDay().toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(get(api("/deliberes/" + delibereId + "/recours")).with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoursExerce").value(false))
                .andExpect(jsonPath("$.delaiExpire").value(false));

        // Délai non exercé → clôture manuelle de l'étape
        passerEtape(dossierId, etapeId, "CLOTURE");
    }

    // ---------------------------------------------------------------- P5

    @Test
    @DisplayName("P5 — Décision devenue définitive : adjudication sur étape CLOTURE puis archivage jurisprudentiel")
    void p5_decisionDefinitive() throws Exception {
        long dossierId = creerDossierRecouvrement();
        long etapeId = premiereEtape(dossierId);
        passerEtape(dossierId, etapeId, "EN_COURS");
        passerEtape(dossierId, etapeId, "EN_DELIBERE");
        mockMvc.perform(post(api("/dossiers/" + dossierId + "/deliberes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "dateDeliberee", LocalDate.now().toString(),
                                "resultat", "FAVORABLE")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated());
        passerEtape(dossierId, etapeId, "CLOTURE");

        mockMvc.perform(post(api("/dossiers/" + dossierId + "/adjudications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "beneficiaire", "Afriland First Bank",
                                "statutComptabilisation", "COMPTABILISE", "repriseProvision", true,
                                "restitutionSoulte", false, "mutation", true, "reliquat", 150000,
                                "dateDecision", LocalDate.now().toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reliquat").value(150000.0));

        MockMultipartFile pdf = new MockMultipartFile("file", "grosse.pdf", "application/pdf", new byte[]{1, 2, 3});
        mockMvc.perform(multipart(api("/jurisprudences"))
                        .file(pdf)
                        .param("dossierId", String.valueOf(dossierId))
                        .param("typeArchive", "GROSSE")
                        .param("motsCles", "saisie", "hypotheque")
                        .param("natureDecision", "Recouvrement")
                        .param("juridiction", "TGI Douala")
                        .param("dateDecision", LocalDate.now().toString())
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated());

        mockMvc.perform(get(api("/jurisprudences")).param("motCle", "saisie")
                        .with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // L'étape peut alors être archivée (aucune transition automatique — Q-60)
        passerEtape(dossierId, etapeId, "ARCHIVE");
    }

    // ---------------------------------------------------------------- P6

    @Test
    @DisplayName("P6 — Demande de frais : dépôt avocat → conformité → opportunité → paiement")
    void p6_demandeDeFrais() throws Exception {
        long dossierId = creerDossierRecouvrement();
        String referenceFacture = "FAC-E2E-" + COMPTEUR.incrementAndGet();

        long demandeId = extraireId(mockMvc.perform(post(api("/frais/demandes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dossierId", dossierId, "montant", 1_000_000,
                                "piecesJustificatives", List.of("facture.pdf"), "referenceFacture", referenceFacture)))
                        .with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutCircuit").value("DEPOSEE"))
                .andReturn());

        // Anti-doublon de facture (ERR-006)
        mockMvc.perform(post(api("/frais/demandes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dossierId", dossierId, "montant", 500_000,
                                "piecesJustificatives", List.of("facture2.pdf"), "referenceFacture", referenceFacture)))
                        .with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR-006"))
                // Le corps nomme le champ fautif : c'est ce qui permet au client de le signaler sur
                // le bon champ plutôt que dans un bandeau générique (QF-21 du frontend).
                .andExpect(jsonPath("$.champ").value("referenceFacture"));

        mockMvc.perform(put(api("/frais/demandes/" + demandeId + "/conformite"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conforme", true)))
                        .with(role("kc-assistante", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCircuit").value("CONFORMITE"));

        // Montant sous le seuil et dossier non sensible → le DJA valide seul
        mockMvc.perform(put(api("/frais/demandes/" + demandeId + "/opportunite"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accord", true)))
                        .with(role("kc-dja", "ROLE_DJA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCircuit").value("VALIDEE"));

        mockMvc.perform(patch(api("/frais/demandes/" + demandeId + "/paiement"))
                        .with(role("kc-assistante", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement").value("PAYEE"));

        // L'avocat retrouve sa demande dans sa file de travail (Q-24)
        mockMvc.perform(get(api("/frais/demandes")).with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------------------------------------------------------------- P7

    @Test
    @DisplayName("P7 — Constitution d'un prestataire : demande juriste → signature SH → lettre en GED")
    void p7_constitutionPrestataire() throws Exception {
        long dossierId = creerDossierRecouvrement();

        long demandeId = extraireId(mockMvc.perform(post(api("/constitutions/prestataires"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dossierId", dossierId, "prestataireId", avocatId,
                                "motif", "Représentation devant le TGI")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andReturn());

        mockMvc.perform(put(api("/constitutions/prestataires/" + demandeId + "/validation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "VALIDEE")))
                        .with(role("kc-sh", "ROLE_SH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.lettreDocumentId").isNotEmpty());

        // La lettre générée est bien un document du dossier (interaction intervenant → ged)
        mockMvc.perform(get(api("/dossiers/" + dossierId + "/documents")).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------------------------------------------------------------- P8

    @Test
    @DisplayName("P8 — Suppression d'un document : demande du juriste auteur → approbation DJ → suppression logique")
    void p8_suppressionDocument() throws Exception {
        long dossierId = creerDossierRecouvrement();

        MockMultipartFile fichier = new MockMultipartFile("file", "a-supprimer.pdf", "application/pdf", new byte[]{1, 2});
        long documentId = extraireId(mockMvc.perform(multipart(api("/ged/documents"))
                        .file(fichier)
                        .param("dossierId", String.valueOf(dossierId))
                        .param("typeDocument", "Pièce erronée")
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());

        // Le juriste auteur ne peut que demander la suppression (202, document intact)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(api("/ged/documents/" + documentId))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isAccepted());

        mockMvc.perform(get(api("/ged/documents/" + documentId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk());

        // Le DJ approuve → suppression logique effective (404 ensuite)
        mockMvc.perform(put(api("/ged/documents/" + documentId + "/approbation-suppression"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROUVEE")))
                        .with(role("kc-dj", "ROLE_DJ")))
                .andExpect(status().isOk());

        mockMvc.perform(get(api("/ged/documents/" + documentId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- P9

    @Test
    @DisplayName("P9 — Pilotage : tableau de bord temps réel, rapport périodique, vue consolidée client")
    void p9_pilotage() throws Exception {
        long dossierId = creerDossierRecouvrement();

        mockMvc.perform(get(api("/tableau-de-bord")).with(role("kc-dj", "ROLE_DJ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbDossiersActifs").isNumber())
                .andExpect(jsonPath("$.tauxSuccesRecouvrement").isNumber());

        mockMvc.perform(get(api("/rapports"))
                        .param("type", "MENSUEL")
                        .param("dateDebut", LocalDate.now().minusDays(1).toString())
                        .param("dateFin", LocalDate.now().plusDays(1).toString())
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbNouveauxDossiers").isNumber());

        // Période sans aucune activité → 204 (RG-REP-02)
        mockMvc.perform(get(api("/rapports"))
                        .param("type", "JOURNALIER")
                        .param("dateDebut", "2000-01-01")
                        .param("dateFin", "2000-01-02")
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isNoContent());

        long clientId = lireJson(mockMvc.perform(get(api("/dossiers/" + dossierId))
                        .with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andReturn()).get("clientId").asLong();

        mockMvc.perform(get(api("/clients/" + clientId + "/dossiers")).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recouvrement.length()").value(1))
                .andExpect(jsonPath("$.exploitationLitiges.length()").value(0));

        // Le tableau de bord reste interdit aux profils non DJ (RG-REP-03)
        mockMvc.perform(get(api("/tableau-de-bord")).with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- Sécurité transverse

    @Test
    @DisplayName("Sécurité — un endpoint métier sans jeton renvoie 401 au format du contrat")
    void securite_sansJetonRenvoie401() throws Exception {
        mockMvc.perform(get(api("/dossiers")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-UNAUTHENTICATED"));
    }

    // ---------------------------------------------------------------- Utilitaires

    private long creerDossierRecouvrement() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client E2E " + n, "reference", "CLI-E2E-" + n)))
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());

        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste").orElseThrow().getId();

        return extraireId(mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.ofEntries(
                                Map.entry("reference", "DOS-E2E-" + n),
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("categorie", "RECOUVREMENT"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-" + n),
                                Map.entry("pvTransfert", "PV-" + n),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)))))
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long premiereEtape(long dossierId) throws Exception {
        return lireJson(mockMvc.perform(get(api("/dossiers/" + dossierId))
                        .with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andReturn()).get("etapes").get(0).get("id").asLong();
    }

    private void passerEtape(long dossierId, long etapeId, String statut) throws Exception {
        mockMvc.perform(patch(api("/dossiers/" + dossierId + "/etapes/" + etapeId + "/statut"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nouveauStatut", statut)))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk());
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

    /**
     * Le post-processeur {@code jwt()} de {@code spring-security-test} n'exécute pas le
     * {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter}
     * de l'application (il dérive ses propres autorités du claim {@code scope}) : les autorités
     * sont donc fournies explicitement, à l'identique de ce que le convertisseur produirait à
     * partir du claim {@code roles} (préfixe vide, cf. `SecurityConfig`).
     */
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
