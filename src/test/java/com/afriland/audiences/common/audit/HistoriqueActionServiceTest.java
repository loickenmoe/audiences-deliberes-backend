package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoriqueActionServiceTest {

    @Mock
    private HistoriqueActionRepository historiqueActionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private HistoriqueActionService historiqueActionService;

    @Test
    void enregistrer_persistsAllFieldsForTheCurrentUser() {
        Utilisateur juriste = Utilisateur.builder().id(7L).nom("Juriste").build();
        when(currentUserService.getCurrentUser()).thenReturn(juriste);
        when(historiqueActionRepository.save(any(HistoriqueAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> details = Map.of("ancienStatut", "OUVERTURE", "nouveauStatut", "EN_COURS");

        HistoriqueAction result = historiqueActionService.enregistrer(
                99L, TypeActionHistorique.ETAPE_STATUT_MODIFIE, details);

        ArgumentCaptor<HistoriqueAction> captor = ArgumentCaptor.forClass(HistoriqueAction.class);
        org.mockito.Mockito.verify(historiqueActionRepository).save(captor.capture());
        HistoriqueAction saved = captor.getValue();
        assertThat(saved.getDossierId()).isEqualTo(99L);
        assertThat(saved.getUtilisateur()).isEqualTo(juriste);
        assertThat(saved.getCode()).isEqualTo(TypeActionHistorique.ETAPE_STATUT_MODIFIE);
        // Q-111 : le libellé n'est plus transmis, il est dérivé du code. Un écart entre les deux,
        // possible tant qu'ils étaient deux arguments côte à côte, ne peut plus se produire.
        assertThat(saved.getAction()).isEqualTo(TypeActionHistorique.ETAPE_STATUT_MODIFIE.libelle());
        assertThat(saved.getDetails()).isEqualTo(details);
        assertThat(result).isSameAs(saved);
    }

    @Test
    void enregistrer_acceptsNullDossierIdForActionsOutsideADossier() {
        Utilisateur dj = Utilisateur.builder().id(1L).build();
        when(currentUserService.getCurrentUser()).thenReturn(dj);
        when(historiqueActionRepository.save(any(HistoriqueAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        HistoriqueAction result = historiqueActionService.enregistrer(null, TypeActionHistorique.CONFIGURATION_MODIFIEE, null);

        assertThat(result.getDossierId()).isNull();
    }

    @Test
    void enregistrerPour_doesNotConsultCurrentUserService() {
        Utilisateur explicit = Utilisateur.builder().id(3L).build();
        when(historiqueActionRepository.save(any(HistoriqueAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        historiqueActionService.enregistrerPour(explicit, 1L, TypeActionHistorique.ALARME_REPROGRAMMEE, null);

        verifyNoInteractions(currentUserService);
    }
}
