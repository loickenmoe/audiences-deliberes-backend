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
 * Monitoring of external notifications on the administration screen. Restricted to DJ and DJA, like
 * the in-app channel monitoring: rows carry recipient contact details and provider errors.
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

    /** Requeues an abandoned send once the cause of its failure has been fixed. */
    @PostMapping("/{id}/relancer")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public void relancer(@PathVariable Long id) {
        supervisionNotificationService.relancer(id);
    }
}
