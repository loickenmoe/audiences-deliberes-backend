package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.dossier.mapper.HistoriqueActionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consultation de l'audit (Q-112).
 *
 * <p>Ce que ces tests protègent n'est pas le filtrage — Spring Data s'en charge — mais les décisions
 * qui rendent l'écran acceptable : la trace de la consultation, sa juste granularité, et le refus
 * des combinaisons de critères qui n'ont pas de sens.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private HistoriqueActionRepository historiqueActionRepository;

    @Mock
    private HistoriqueActionMapper historiqueActionMapper;

    @Mock
    private HistoriqueActionService historiqueActionService;

    @InjectMocks
    private AuditService auditService;

    private static Specification<HistoriqueAction> specification() {
        return ArgumentMatchers.any();
    }

    private void lectureVide() {
        when(historiqueActionRepository.findAll(specification(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(historiqueActionRepository.actionsPresentes()).thenReturn(List.of());
    }

    /**
     * Le filtre par personne étant autorisé, cet écran permet de suivre nommément l'activité d'un
     * salarié. La trace de son usage protège le DJ et la DJA autant que ceux qu'ils consultent :
     * elle démontre qu'ils n'ont regardé que ce qu'ils devaient.
     */
    @Test
    void consulter_historiseLaConsultationAvecSesCriteres() {
        lectureVide();

        auditService.consulter(Perimetre.TOUT, null, 42L, TypeActionHistorique.DOCUMENT_CHARGE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0, 10);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(historiqueActionService).enregistrer(
                eq(null), eq(TypeActionHistorique.AUDIT_CONSULTE), captor.capture());
        Map<String, Object> criteres = captor.getValue();
        assertThat(criteres).containsEntry("perimetre", "TOUT")
                .containsEntry("codeRecherche", "DOCUMENT_CHARGE")
                .containsEntry("dateDebut", "2026-09-01")
                .containsEntry("dateFin", "2026-09-30");
        // Nommé `auteurRecherche` : `utilisateurId` désigne déjà, sur la ligne d'historique, celui
        // qui a consulté. Les confondre rendrait la trace illisible.
        assertThat(criteres).containsEntry("auteurRecherche", 42L);
    }

    /** Un filtre absent n'est pas consigné : une trace pleine de « null » se lit moins bien. */
    @Test
    void consulter_neConsignePasLesCriteresAbsents() {
        lectureVide();

        auditService.consulter(Perimetre.TOUT, null, null, null, null, null, 0, 10);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(historiqueActionService).enregistrer(any(), eq(TypeActionHistorique.AUDIT_CONSULTE), captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys("perimetre");
    }

    /**
     * Feuilleter un résultat n'est pas une nouvelle consultation. Compter chaque page produirait un
     * bruit qui noierait précisément ce que la trace sert à voir.
     */
    @Test
    void consulter_neTracePasLeFeuilletage() {
        lectureVide();

        auditService.consulter(Perimetre.TOUT, null, null, null, null, null, 3, 10);

        verify(historiqueActionService, never()).enregistrer(any(), any(), any());
    }

    @Test
    void consulter_litDuPlusRecentAuPlusAncien_idDepartageant() {
        lectureVide();

        auditService.consulter(Perimetre.TOUT, null, null, null, null, null, 0, 10);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(historiqueActionRepository).findAll(specification(), captor.capture());
        List<Sort.Order> ordres = captor.getValue().getSort().toList();
        assertThat(ordres).extracting(Sort.Order::getProperty).containsExactly("dateAction", "id");
        assertThat(ordres.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    /**
     * Un périmètre « un dossier » sans dossier renverrait tout l'historique : le pire des résultats,
     * puisqu'il ressemble à une réponse. Refusé plutôt qu'interprété.
     */
    @Test
    void consulter_refuseUnPerimetreDossierSansDossier() {
        assertThatThrownBy(() -> auditService.consulter(Perimetre.UN_DOSSIER, null, null, null, null, null, 0, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("identifiant du dossier");

        verify(historiqueActionService, never()).enregistrer(any(), any(), any());
    }

    @Test
    void consulter_refuseUneFenetreInversee() {
        assertThatThrownBy(() -> auditService.consulter(Perimetre.TOUT, null, null, null,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), 0, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("date de début");
    }

    /**
     * La liste des natures proposées ne dépend pas des filtres actifs : sinon en choisir une ferait
     * disparaître les autres, et l'on ne pourrait plus changer d'avis sans tout vider.
     */
    @Test
    void consulter_proposeToutesLesNaturesPresentes() {
        when(historiqueActionRepository.findAll(specification(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(historiqueActionRepository.actionsPresentes()).thenReturn(List.of(
                TypeActionHistorique.DOSSIER_CREE, TypeActionHistorique.DOCUMENT_CHARGE));

        AuditResponse audit = auditService.consulter(Perimetre.TOUT, null, null,
                TypeActionHistorique.DOSSIER_CREE, null, null, 0, 10);

        assertThat(audit.actionsDisponibles())
                .extracting(AuditResponse.OptionAction::libelle)
                .containsExactly("Création du dossier", "Chargement d'un document");
    }
}
