package com.afriland.audiences.notification.service;

import com.afriland.audiences.notification.domain.NotificationEnvoi;
import com.afriland.audiences.notification.repository.NotificationEnvoiRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scheduled pass abandons stale attachments inside a real transaction. A {@code @Transactional}
 * method called from the same class is not proxied, so the change would never be flushed.
 */
class DispatcherNotificationTest {

    private final PlatformTransactionManager gestionnaire = mock(PlatformTransactionManager.class);
    private final NotificationEnvoiRepository depot = mock(NotificationEnvoiRepository.class);
    private final RemiseNotification remise = mock(RemiseNotification.class);
    private final DispatcherNotification dispatcher =
            new DispatcherNotification(depot, remise, new TransactionTemplate(gestionnaire));

    @Test
    void l_abandon_des_pieces_jointes_se_fait_dans_une_transaction_validee() {
        TransactionStatus statut = new SimpleTransactionStatus();
        when(gestionnaire.getTransaction(any())).thenReturn(statut);
        NotificationEnvoi envoi = mock(NotificationEnvoi.class);
        when(depot.trouverEnAttenteDePieceJointe(any(), any())).thenReturn(List.of(envoi));
        when(depot.trouverARemettre(any(), any())).thenReturn(List.of());

        dispatcher.remettreLesNotificationsEnAttente();

        InOrder ordre = inOrder(gestionnaire, envoi);
        ordre.verify(gestionnaire).getTransaction(any());
        ordre.verify(envoi).pieceJointeAbandonnee("DEPOT_SANS_REPONSE");
        ordre.verify(gestionnaire).commit(statut);
        verify(gestionnaire, never()).rollback(any());
    }

    @Test
    void chaque_notification_a_remettre_est_confiee_a_la_remise() {
        when(gestionnaire.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(depot.trouverEnAttenteDePieceJointe(any(), any())).thenReturn(List.of());
        when(depot.trouverARemettre(any(), any())).thenReturn(List.of(7L, 8L));

        dispatcher.remettreLesNotificationsEnAttente();

        verify(remise).remettre(7L);
        verify(remise).remettre(8L);
    }
}
