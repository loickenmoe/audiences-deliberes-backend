package com.afriland.audiences.notification.service;

import com.afriland.audiences.referentiel.domain.ConfigurationSysteme;
import com.afriland.audiences.referentiel.repository.ConfigurationSystemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads the notification settings in a single query. No caching: a changed setting must apply to
 * the very next event, and reading about fifteen rows is negligible next to the business transaction.
 */
@Service
@RequiredArgsConstructor
public class ReglagesNotificationService {

    private final ConfigurationSystemeRepository configurationSystemeRepository;

    @Transactional(readOnly = true)
    public ReglagesNotification charger() {
        Map<String, String> valeurs = configurationSystemeRepository.findAll().stream()
                .filter(configuration -> estReglageDeNotification(configuration.getCle()))
                .collect(Collectors.toMap(ConfigurationSysteme::getCle, ConfigurationSysteme::getValeur));
        return ReglagesNotification.depuis(valeurs);
    }

    private static boolean estReglageDeNotification(String cle) {
        return cle.equals(ReglagesNotification.CLE_CANAUX_INTERNES)
                || cle.equals(ReglagesNotification.CLE_CANAUX_PRESTATAIRES)
                || cle.startsWith(ReglagesNotification.PREFIXE_GRILLE);
    }
}
