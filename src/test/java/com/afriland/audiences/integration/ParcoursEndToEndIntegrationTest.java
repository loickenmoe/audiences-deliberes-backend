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
 * End-to-end integration tests of the 9 main user journeys.
 * <p>
 * Full Spring context (security included), real Flyway-migrated PostgreSQL (Testcontainers), real
 * HTTP calls via {@link MockMvc}. Two substitutions:
 * <ul>
 *   <li>authentication uses a simulated JWT ({@code spring-security-test}) with the same claims as
 *       the real token; only cryptographic verification is bypassed;</li>
 *   <li>{@link MinioClient} is mocked; object storage itself is out of scope.</li>
 * </ul>
 * These tests validate the <b>full wiring</b> (routes, security, transactions, cross-module
 * interactions, persistence), not unit business rules covered by service tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ParcoursEndToEndIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
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
        // Users are pre-provisioned so journeys stay deterministic (known identifiers and profiles).
        // First-call provisioning in a read-only transaction (R-06) is covered by
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

    // P1

    @Test
    @DisplayName("P1 — Ouverture d'un dossier de recouvrement : création, affectations, pièce GED, étape EN_COURS")
    void p1_ouvertureDossierRecouvrement() throws Exception {
        long dossierId = creerDossierRecouvrement();

        // Parties are mandatory and may be multiple. The case created above has two defendants; check
        // they come back as is, then that their absence is refused on input.
        mockMvc.perform(get(api("/dossiers/" + dossierId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parties.demandeurs.length()").value(1))
                .andExpect(jsonPath("$.parties.defendeurs.length()").value(2));

        // The legal officer uploads a document to the GED
        MockMultipartFile fichier = new MockMultipartFile("file", "piece.pdf", "application/pdf", new byte[]{1, 2, 3});
        mockMvc.perform(multipart(api("/ged/documents"))
                        .file(fichier)
                        .param("dossierId", String.valueOf(dossierId))
                        .param("typeDocument", "Pièce de procédure")
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated());

        // INSTANCE step: OUVERTURE -> EN_COURS
        long etapeId = premiereEtape(dossierId);
        mockMvc.perform(patch(api("/dossiers/" + dossierId + "/etapes/" + etapeId + "/statut"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nouveauStatut", "EN_COURS")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCycleVie").value("EN_COURS"));

        // The consolidated case exposes assignments and history. The `documents` field of
        // `DossierResponse` stays empty **by design**: the `dossier` module cannot depend on `ged` (layer
        // inversion); documents are served by `GET /dossiers/{id}/documents`.
        mockMvc.perform(get(api("/dossiers/" + dossierId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(0))
                .andExpect(jsonPath("$.historique.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.avocatsAffectes.length()").value(1));

        mockMvc.perform(get(api("/dossiers/" + dossierId + "/documents")).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                // The Documents tab is paginated: the total lives in the envelope.
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // P2

    @Test
    @DisplayName("P2 — Cycle d'une audience : planification, compte rendu juriste, CR avocat validé par l'Assistante")
    void p2_cycleAudience() throws Exception {
        long dossierId = creerDossierRecouvrement();
        long etapeId = premiereEtape(dossierId);
        passerEtape(dossierId, etapeId, "EN_COURS");

        // RG-AUD-08: the date cannot precede the case opening...
        long audienceId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/audiences"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "datePlanifiee", LocalDate.now().plusDays(7).toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andReturn());

        // ...while RG-AUD-01 requires the hearing to be past before entering its report: time is
        // simulated by backdating the row, the only way to run the full journey in one test (the refusal
        // for a future hearing is unit-tested).
        audienceRepository.findById(audienceId).ifPresent(audience -> {
            audience.setDatePlanifiee(LocalDate.now().minusDays(1));
            audienceRepository.save(audience);
        });

        // Internal report by the legal officer (Epic 2)
        mockMvc.perform(put(api("/audiences/" + audienceId + "/compte-rendu"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("compteRendu", "Audience tenue, affaire mise en délibéré")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TENUE"));

        // Report published by the lawyer (Epic 5), validated by the Assistant. The narrative carries its
        // attachment, uploaded in the same multipart request.
        MockMultipartFile plumitif =
                new MockMultipartFile("file", "plumitif.pdf", "application/pdf", new byte[]{4, 5, 6});
        long publicationId = extraireId(mockMvc.perform(multipart(api("/publications/cr-audience"))
                        .file(plumitif)
                        .param("dossierId", String.valueOf(dossierId))
                        .param("audienceId", String.valueOf(audienceId))
                        .param("contenu", "Plaidoirie effectuée, renvoi au délibéré")
                        .with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isCreated())
                .andReturn());

        // The narrative stays mandatory with multipart: it must be refused on input (400), not on
        // flush (500).
        mockMvc.perform(multipart(api("/publications/cr-audience"))
                        .param("dossierId", String.valueOf(dossierId))
                        .param("audienceId", String.valueOf(audienceId))
                        .param("contenu", "   ")
                        .with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isBadRequest());

        // The Assistant reads the publication to validate, with names.
        mockMvc.perform(get(api("/publications/" + publicationId)).with(role("kc-assistante", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutValidation").value("DEPOSE"))
                .andExpect(jsonPath("$.referenceDossier").isNotEmpty())
                .andExpect(jsonPath("$.avocatNom").isNotEmpty())
                .andExpect(jsonPath("$.audienceDate").isNotEmpty())
                // The report attachment is read like any other publication's.
                .andExpect(jsonPath("$.nomFichier").value("plumitif.pdf"));

        // RG-INT-06: the report is open to ALL authorized users as soon as it is uploaded, without
        // waiting for the Assistant's validation.
        mockMvc.perform(get(api("/publications/" + publicationId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutValidation").value("DEPOSE"));

        // ... and they are notified ON UPLOAD, not on validation.
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type == 'NOUVELLE_PUBLICATION')]").isNotEmpty());

        // The SH has no specific regime: reads like the others, from upload.
        mockMvc.perform(get(api("/publications/" + publicationId)).with(role("kc-sh", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutValidation").value("DEPOSE"));

        // A report cannot be rejected: the Assistant only acknowledges receipt.
        mockMvc.perform(put(api("/publications/" + publicationId + "/validation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("statut", "REJETE", "motifRejet", "Récit incomplet")))
                        .with(role("kc-assistante", "ROLE_ASSISTANTE")))
                .andExpect(status().isBadRequest());

        // The case tab lists it for the assigned legal officer, without status filter.
        mockMvc.perform(get(api("/publications")).param("dossierId", String.valueOf(dossierId))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + publicationId + ")]").isNotEmpty());

        mockMvc.perform(put(api("/publications/" + publicationId + "/validation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("statut", "VALIDE")))
                        .with(role("kc-assistante", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutValidation").value("VALIDE"));

        // Once validated, the publication becomes visible, with its exchanges
        mockMvc.perform(get(api("/publications/" + publicationId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentaires").isArray());

        // The acknowledgement adds no alert: everyone could already read.
    }

    // P3

    @Test
    @DisplayName("P3 — Cycle d'un délibéré (Q-78) : mise en délibéré, prorogation, rabattement, nouvelle mise en délibéré, vidage")
    void p3_cycleDelibere() throws Exception {
        long dossierId = creerDossierRecouvrement();
        long etapeId = premiereEtape(dossierId);
        passerEtape(dossierId, etapeId, "EN_COURS");
        // A step goes under deliberation only with a held hearing and none pending.
        tenirUneAudience(dossierId, etapeId);
        passerEtape(dossierId, etapeId, "EN_DELIBERE");
        LocalDate annoncee = LocalDate.now().plusDays(7);
        LocalDate prorogee = LocalDate.now().plusDays(14);

        // Put under deliberation: no result, the step stays EN_DELIBERE.
        long premier = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/deliberes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "dateDeliberee", annoncee.toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE"))
                .andReturn());

        // Only one pending deliberation per step.
        mockMvc.perform(post(api("/dossiers/" + dossierId + "/deliberes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId, "dateDeliberee", annoncee.toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isConflict());

        // "DELIBERE PROROGE AU [date]": while pending, a future date that becomes the announced date.
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

        // "DELIBERE RABATTU LE [date, reason]": the judge reopens hearings before ruling.
        mockMvc.perform(post(api("/deliberes/" + premier + "/rabattement"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", LocalDate.now().toString(), "motif", "Réouverture des débats")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutCycleVie").value("EN_COURS"));

        // A reopened deliberation can no longer be extended.
        mockMvc.perform(post(api("/deliberes/" + premier + "/prorogations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", prorogee.toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isBadRequest());

        // New deliberation, then the decision is rendered: the deliberation is closed.
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

        // After the decision, neither extension nor reopening.
        mockMvc.perform(post(api("/deliberes/" + second + "/rabattement"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", LocalDate.now().toString(), "motif", "Trop tard")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isBadRequest());

        // The case tab shows both passes: the closed one, then the reopened one with its reason.
        mockMvc.perform(get(api("/dossiers/" + dossierId + "/deliberes")).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etat").value("VIDE"))
                .andExpect(jsonPath("$[1].etat").value("RABATTU"))
                .andExpect(jsonPath("$[1].motifRabattement").value("Réouverture des débats"))
                .andExpect(jsonPath("$[1].nombreProrogations").value(1));
    }

    // P4

    @Test
    @DisplayName("P4 — Voie de recours : délibéré défavorable, échéance de recours consultable, clôture")
    void p4_voieDeRecours() throws Exception {
        long dossierId = creerDossierRecouvrement();
        long etapeId = premiereEtape(dossierId);
        passerEtape(dossierId, etapeId, "EN_COURS");
        // A step goes under deliberation only with a held hearing and none pending.
        tenirUneAudience(dossierId, etapeId);
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

        // Deadline not exercised -> manual closing of the step
        passerEtape(dossierId, etapeId, "CLOTURE");
    }

    // P5

    @Test
    @DisplayName("P5 — Décision devenue définitive : adjudication sur étape CLOTURE puis archivage jurisprudentiel")
    void p5_decisionDefinitive() throws Exception {
        long dossierId = creerDossierRecouvrement();
        long etapeId = premiereEtape(dossierId);
        passerEtape(dossierId, etapeId, "EN_COURS");
        // A step goes under deliberation only with a held hearing and none pending.
        tenirUneAudience(dossierId, etapeId);
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

        // The step can then be archived (no automatic transition)
        passerEtape(dossierId, etapeId, "ARCHIVE");
    }

    // P6

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

        // Duplicate invoice check (ERR-006)
        mockMvc.perform(post(api("/frais/demandes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dossierId", dossierId, "montant", 500_000,
                                "piecesJustificatives", List.of("facture2.pdf"), "referenceFacture", referenceFacture)))
                        .with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR-006"))
                // The body names the faulty field, so the client can flag it on the right field rather than in a
                // generic banner.
                .andExpect(jsonPath("$.champ").value("referenceFacture"));

        mockMvc.perform(put(api("/frais/demandes/" + demandeId + "/conformite"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conforme", true)))
                        .with(role("kc-assistante", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCircuit").value("CONFORMITE"));

        // Amount under the threshold and non-sensitive case -> the DJA validates alone
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

        // Detail: case and lawyer named, workflow decisions in order
        mockMvc.perform(get(api("/frais/demandes/" + demandeId)).with(role("kc-assistante", "ROLE_ASSISTANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceDossier").isNotEmpty())
                .andExpect(jsonPath("$.avocatNom").isNotEmpty())
                .andExpect(jsonPath("$.validationConjointeRequise").value(false))
                .andExpect(jsonPath("$.validations.length()").value(2))
                .andExpect(jsonPath("$.validations[0].etape").value("CONFORMITE"))
                .andExpect(jsonPath("$.validations[1].etape").value("OPPORTUNITE"));

        // The lawyer reads their own request, never a colleague's
        mockMvc.perform(get(api("/frais/demandes/" + demandeId)).with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isOk());
        mockMvc.perform(get(api("/frais/demandes/" + demandeId)).with(role("kc-avocat-etranger", "ROLE_AVOCAT")))
                .andExpect(status().isForbidden());

        // The lawyer finds the request in their work queue
        mockMvc.perform(get(api("/frais/demandes")).with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // P7

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
                .andExpect(jsonPath("$.lettreDocumentId").isNotEmpty())
                // The request is named; an on-demand lawyer remains a lawyer
                .andExpect(jsonPath("$.referenceDossier").isNotEmpty())
                .andExpect(jsonPath("$.prestataireNom").value("Me Test"))
                .andExpect(jsonPath("$.prestataireType").value("AVOCAT"));

        // The retained lawyer is notified
        mockMvc.perform(get(api("/alertes/mes-notifications")).with(role("kc-avocat", "ROLE_AVOCAT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type == 'CONSTITUTION_A_SIGNER')]").isNotEmpty());

        // The generated letter is a case document (intervenant -> ged interaction)
        mockMvc.perform(get(api("/dossiers/" + dossierId + "/documents")).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk())
                // The Documents tab is paginated: the total lives in the envelope.
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // P8

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

        // The authoring legal officer can only request deletion (202, document intact)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(api("/ged/documents/" + documentId))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isAccepted());

        mockMvc.perform(get(api("/ged/documents/" + documentId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isOk());

        // The DJ approves -> effective soft delete (404 afterwards)
        mockMvc.perform(put(api("/ged/documents/" + documentId + "/approbation-suppression"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROUVEE")))
                        .with(role("kc-dj", "ROLE_DJ")))
                .andExpect(status().isOk());

        mockMvc.perform(get(api("/ged/documents/" + documentId)).with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andExpect(status().isNotFound());
    }

    // P9

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

        // Period without any activity -> 204 (RG-REP-02)
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

        // The DJA has access too: she co-validates and instructs the same cases.
        mockMvc.perform(get(api("/tableau-de-bord")).with(role("kc-dja", "ROLE_DJA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbDossiersActifs").isNumber());

        // Still forbidden to other profiles (RG-REP-03).
        mockMvc.perform(get(api("/tableau-de-bord")).with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(api("/tableau-de-bord")).with(role("kc-assistante", "ROLE_ASSISTANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sécurité — un endpoint métier sans jeton renvoie 401 au format du contrat")
    void securite_sansJetonRenvoie401() throws Exception {
        mockMvc.perform(get(api("/dossiers")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-UNAUTHENTICATED"));
    }

    /**
     * A litigation case states who opposes whom, or is not created. The refusal targets the field so
     * the screen knows where to show the message.
     */
    @Test
    void lesPartiesSontObligatoiresALaCreation() throws Exception {
        int n = COMPTEUR.incrementAndGet();
        long clientId = extraireId(mockMvc.perform(post(api("/clients"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nom", "Client parties " + n)))
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());
        Long juristeId = utilisateurRepository.findByKeycloakId("kc-juriste").orElseThrow().getId();

        Map<String, Object> socle = Map.ofEntries(
                Map.entry("nature", "Assignation en paiement"),
                Map.entry("categorie", "RECOUVREMENT"),
                Map.entry("juridictionSaisie", "TGI Douala"),
                Map.entry("clientId", clientId),
                Map.entry("risqueEncouru", 1_000_000),
                Map.entry("juristesAffectes", List.of(juristeId)),
                Map.entry("avocatsAffectes", List.of(avocatId)));

        // No parties at all.
        mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(socle))
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isBadRequest());

        // One empty side: "no defendant" is not a defendant.
        Map<String, Object> sansDefendeur = new java.util.HashMap<>(socle);
        sansDefendeur.put("parties", Map.of(
                "demandeurs", List.of("AFRILAND FIRST BANK"),
                "defendeurs", List.of()));
        mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(sansDefendeur))
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isBadRequest());

        // An empty name: a party without a name names nobody.
        Map<String, Object> nomVide = new java.util.HashMap<>(socle);
        nomVide.put("parties", Map.of(
                "demandeurs", List.of("   "),
                "defendeurs", List.of("Débiteur")));
        mockMvc.perform(post(api("/dossiers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(nomVide))
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isBadRequest());
    }

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
                                Map.entry("nature", "Assignation en paiement"),
                                Map.entry("categorie", "RECOUVREMENT"),
                                Map.entry("juridictionSaisie", "TGI Douala"),
                                Map.entry("clientId", clientId),
                                Map.entry("risqueEncouru", 1_000_000),
                                Map.entry("dossierCreditOrigine", "CRD-" + n),
                                Map.entry("pvTransfert", "PV-" + n),
                                Map.entry("juristesAffectes", List.of(juristeId)),
                                Map.entry("avocatsAffectes", List.of(avocatId)),
                                // Mandatory: at least one claimant and one defendant, several allowed.
                                Map.entry("parties", Map.of(
                                        "demandeurs", List.of("AFRILAND FIRST BANK"),
                                        "defendeurs", List.of("Débiteur " + n, "Caution " + n))),
                                // Mandatory: 00002 = Bonanjo.
                                Map.entry("codeAgenceOrigine", "00002"))))
                        .with(role("kc-juriste", "ROLE_JURISTE", "ROLE_SAISIE")))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long premiereEtape(long dossierId) throws Exception {
        return lireJson(mockMvc.perform(get(api("/dossiers/" + dossierId))
                        .with(role("kc-juriste", "ROLE_CONSULTATION")))
                .andReturn()).get("etapes").get(0).get("id").asLong();
    }

    /**
     * Holds a hearing on the step: scheduling, backdating, report.
     *
     * <p>A step goes under deliberation only if a hearing was <b>held</b> there (reported: the
     * {@code TENUE} status is set when the report is entered) and none remains scheduled. Backdating is
     * needed because RG-AUD-01 requires a past hearing to enter its report.
     */
    private void tenirUneAudience(long dossierId, long etapeId) throws Exception {
        long audienceId = extraireId(mockMvc.perform(post(api("/dossiers/" + dossierId + "/audiences"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("etapeId", etapeId,
                                "datePlanifiee", LocalDate.now().plusDays(7).toString())))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isCreated())
                .andReturn());

        audienceRepository.findById(audienceId).ifPresent(audience -> {
            audience.setDatePlanifiee(LocalDate.now().minusDays(1));
            audienceRepository.save(audience);
        });

        mockMvc.perform(put(api("/audiences/" + audienceId + "/compte-rendu"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("compteRendu", "Audience tenue, affaire mise en délibéré")))
                        .with(role("kc-juriste", "ROLE_JURISTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TENUE"));
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
     * The {@code jwt()} post-processor of {@code spring-security-test} does not run the application's
     * {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter}
     * (it derives authorities from the {@code scope} claim), so authorities are provided explicitly,
     * as the converter would produce them from the {@code roles} claim (empty prefix, see `SecurityConfig`).
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
