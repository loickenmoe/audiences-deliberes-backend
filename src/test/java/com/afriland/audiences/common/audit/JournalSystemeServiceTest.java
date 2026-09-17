package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.dossier.mapper.HistoriqueActionMapper;
import com.afriland.audiences.dossier.web.dto.HistoriqueActionResponse;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Journal des actions hors dossier (Q-110).
 *
 * <p>Ce que ces tests protègent n'est pas le filtrage — Spring Data s'en charge — mais les trois
 * décisions qui font la valeur de l'écran : l'ordre de lecture, la liste de choix indépendante des
 * filtres, et le refus d'une fenêtre inversée.
 */
@ExtendWith(MockitoExtension.class)
class JournalSystemeServiceTest {

    @Mock
    private HistoriqueActionRepository historiqueActionRepository;

    @Mock
    private HistoriqueActionMapper historiqueActionMapper;

    @InjectMocks
    private JournalSystemeService journalSystemeService;

    /**
     * La spécification construite par le service ne s'inspecte pas utilement — c'est une lambda
     * opaque. On vérifie donc ce qui l'entoure : le tri, la liste de choix, le refus d'une fenêtre
     * inversée. Ce matcher typé évite la conversion non contrôlée qu'impose {@code any(Class)}.
     */
    private static Specification<HistoriqueAction> specification() {
        return ArgumentMatchers.any();
    }

    private HistoriqueAction ligne(String action) {
        return HistoriqueAction.builder().id(1L).dossierId(null).action(action)
                .details(Map.of("ancienneValeur", "10", "nouvelleValeur", "11"))
                .dateAction(LocalDateTime.now()).build();
    }

    /**
     * Le plus récent d'abord, `id` départageant les ex æquo. Sans ce second critère, deux actions du
     * même instant peuvent changer de place d'une page à l'autre et une ligne se perdre entre deux
     * pages — le défaut établi en Q-92 pour les listes paginées.
     */
    @Test
    void consulter_litDuPlusRecentAuPlusAncien_idDepartageant() {
        when(historiqueActionRepository.findAll(specification(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(historiqueActionRepository.actionsHorsDossier()).thenReturn(List.of());

        journalSystemeService.consulter(null, null, null, null, 0, 10);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        org.mockito.Mockito.verify(historiqueActionRepository).findAll(specification(), captor.capture());
        List<Sort.Order> ordres = captor.getValue().getSort().toList();
        assertThat(ordres).hasSize(2);
        assertThat(ordres.get(0).getProperty()).isEqualTo("dateAction");
        assertThat(ordres.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(ordres.get(1).getProperty()).isEqualTo("id");
    }

    /**
     * Les libellés proposés viennent de **tout** le journal, jamais du résultat filtré : sinon
     * choisir un libellé ferait disparaître les autres choix, et l'on ne pourrait plus changer
     * d'avis sans vider ses filtres.
     */
    @Test
    void consulter_proposeTousLesLibellesMemeQuandUnFiltreEstActif() {
        when(historiqueActionRepository.findAll(specification(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(ligne("Modification de la configuration SEUIL_PROROGATIONS"))));
        when(historiqueActionMapper.toResponse(any())).thenReturn(
                new HistoriqueActionResponse(1L, 7L, "Modification de la configuration SEUIL_PROROGATIONS",
                        Map.of(), LocalDateTime.now()));
        when(historiqueActionRepository.actionsHorsDossier()).thenReturn(List.of(
                "Indexation d'une jurisprudence",
                "Modification de la configuration SEUIL_MONTANT_DEFAUT",
                "Modification de la configuration SEUIL_PROROGATIONS"));

        JournalSystemeResponse journal = journalSystemeService.consulter(
                null, "Modification de la configuration SEUIL_PROROGATIONS", null, null, 0, 10);

        assertThat(journal.content()).hasSize(1);
        assertThat(journal.actionsDisponibles()).hasSize(3);
    }

    @Test
    void consulter_refuseUneFenetreInversee() {
        assertThatThrownBy(() -> journalSystemeService.consulter(
                null, null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), 0, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("date de début");
    }

    /** Une seule borne suffit : « depuis le 1er » et « jusqu'au 30 » sont des questions légitimes. */
    @Test
    void consulter_accepteUneSeuleBorneDeDate() {
        when(historiqueActionRepository.findAll(specification(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(historiqueActionRepository.actionsHorsDossier()).thenReturn(List.of());

        assertThat(journalSystemeService.consulter(null, null, LocalDate.of(2026, 9, 1), null, 0, 10))
                .isNotNull();
    }
}
