package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LettreConstitutionServiceTest {

    private final LettreConstitutionService service = new LettreConstitutionService();

    @Test
    void generer_producesValidPdf() {
        DemandeConstitution demande = DemandeConstitution.builder()
                .id(100L)
                .dossier(Dossier.builder().id(1L).reference("DOS-2026-001").build())
                .prestataire(Avocat.builder().id(5L).nom("Me Fotso").build())
                .demandeur(Utilisateur.builder().id(10L).nom("Juriste").prenom("Test").build())
                .validePar(Utilisateur.builder().id(30L).nom("Sup").prenom("Hierarchique").build())
                .motif("Besoin local")
                .statut(StatutConstitution.VALIDEE)
                .dateDecision(LocalDateTime.now())
                .build();

        byte[] pdf = service.generer(demande);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
