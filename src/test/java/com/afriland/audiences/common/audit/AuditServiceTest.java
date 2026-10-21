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
 * Audit consultation.
 *
 * <p>These tests protect not the filtering (handled by Spring Data) but the decisions that make
 * the screen acceptable: logging the consultation at the right granularity, and rejecting
 * meaningless combinations of criteria.
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
     * Filtering by person allows tracking an employee's activity by name. Logging its use protects the
     * DJ and the DJA as much as those consulted: it shows they only looked at what they had to.
     */
    @Test
    void consulter_historiseLaConsultationAvecSesCriteres() {
        lectureVide();

        auditService.consulter(new FiltresAudit(Perimetre.TOUT, null, 42L, TypeActionHistorique.DOCUMENT_CHARGE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), 0, 10);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(historiqueActionService).enregistrer(
                eq(null), eq(TypeActionHistorique.AUDIT_CONSULTE), captor.capture());
        Map<String, Object> criteres = captor.getValue();
        assertThat(criteres).containsEntry("perimetre", "TOUT")
                .containsEntry("codeRecherche", "DOCUMENT_CHARGE")
                .containsEntry("dateDebut", "2026-09-01")
                .containsEntry("dateFin", "2026-09-30");
        // Named `auteurRecherche` because `utilisateurId` already designates, on the history row, the
        // person who consulted. Mixing them up would make the log unreadable.
        assertThat(criteres).containsEntry("auteurRecherche", 42L);
    }

    /** An absent filter is not logged: a trace full of "null" is harder to read. */
    @Test
    void consulter_neConsignePasLesCriteresAbsents() {
        lectureVide();

        auditService.consulter(new FiltresAudit(Perimetre.TOUT, null, null, null, null, null), 0, 10);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(historiqueActionService).enregistrer(any(), eq(TypeActionHistorique.AUDIT_CONSULTE), captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys("perimetre");
    }

    /**
     * Paging through results is not a new consultation; logging each page would drown out what the
     * log is for.
     */
    @Test
    void consulter_neTracePasLeFeuilletage() {
        lectureVide();

        auditService.consulter(new FiltresAudit(Perimetre.TOUT, null, null, null, null, null), 3, 10);

        verify(historiqueActionService, never()).enregistrer(any(), any(), any());
    }

    @Test
    void consulter_litDuPlusRecentAuPlusAncien_idDepartageant() {
        lectureVide();

        auditService.consulter(new FiltresAudit(Perimetre.TOUT, null, null, null, null, null), 0, 10);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(historiqueActionRepository).findAll(specification(), captor.capture());
        List<Sort.Order> ordres = captor.getValue().getSort().toList();
        assertThat(ordres).extracting(Sort.Order::getProperty).containsExactly("dateAction", "id");
        assertThat(ordres.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    /**
     * A "single case" scope without a case would return the whole history, the worst result since it
     * looks like an answer. Refused rather than interpreted.
     */
    @Test
    void consulter_refuseUnPerimetreDossierSansDossier() {
        var filtres = new FiltresAudit(Perimetre.UN_DOSSIER, null, null, null, null, null);
        assertThatThrownBy(() -> auditService.consulter(filtres, 0, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("identifiant du dossier");

        verify(historiqueActionService, never()).enregistrer(any(), any(), any());
    }

    @Test
    void consulter_refuseUneFenetreInversee() {
        var date2 = LocalDate.of(2026, 9, 30);
        var date3 = LocalDate.of(2026, 9, 1);
        var filtres = new FiltresAudit(Perimetre.TOUT, null, null, null, date2, date3);
        assertThatThrownBy(() -> auditService.consulter(filtres, 0, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("date de début");
    }

    /**
     * The list of offered action types does not depend on the active filters; otherwise picking one
     * would hide the others.
     */
    @Test
    void consulter_proposeToutesLesNaturesPresentes() {
        when(historiqueActionRepository.findAll(specification(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(historiqueActionRepository.actionsPresentes()).thenReturn(List.of(
                TypeActionHistorique.DOSSIER_CREE, TypeActionHistorique.DOCUMENT_CHARGE));

        AuditResponse audit = auditService.consulter(new FiltresAudit(Perimetre.TOUT, null, null, TypeActionHistorique.DOSSIER_CREE, null, null), 0, 10);

        assertThat(audit.actionsDisponibles())
                .extracting(AuditResponse.OptionAction::libelle)
                .containsExactly("Création du dossier", "Chargement d'un document");
    }
}
