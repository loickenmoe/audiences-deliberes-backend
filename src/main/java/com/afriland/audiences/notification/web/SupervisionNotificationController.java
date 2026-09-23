package com.afriland.audiences.notification.web;

import com.afriland.audiences.notification.service.SupervisionNotificationService;
import com.afriland.audiences.notification.web.dto.SupervisionNotificationsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Supervision des notifications externes, sur l'écran d'administration (D17).
 * <p>
 * <b>Réservée au DJ et à la DJA</b>, comme la supervision du canal applicatif (Q-109) : ces lignes
 * portent les coordonnées des destinataires et les erreurs des fournisseurs, qui n'ont pas à être
 * ouvertes à tous les profils internes.
 */
@RestController
@RequestMapping("${api.prefix}/notifications")
@RequiredArgsConstructor
public class SupervisionNotificationController {

    private final SupervisionNotificationService supervisionNotificationService;

    @GetMapping("/supervision")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public SupervisionNotificationsResponse superviser(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return supervisionNotificationService.superviser(dateDebut, dateFin);
    }

    /** Remet en file un envoi abandonné, une fois corrigée la cause de son échec. */
    @PostMapping("/{id}/relancer")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public void relancer(@PathVariable Long id) {
        supervisionNotificationService.relancer(id);
    }
}
