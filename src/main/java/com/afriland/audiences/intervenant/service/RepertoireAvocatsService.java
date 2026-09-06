package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.intervenant.web.dto.AvocatRepertoireResponse;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** FR-INT-08/US 5.8 (RG-INT-01) — cf. FUNCTIONAL_MAP.md §5, API_IMPLEMENTATION_STATUS.md §7. */
@Service
@RequiredArgsConstructor
public class RepertoireAvocatsService {

    private static final List<StatutCycleVie> STATUTS_FERMES = List.of(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE);

    private final AvocatRepository avocatRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final DelibereRepository delibereRepository;

    @Transactional(readOnly = true)
    public List<AvocatRepertoireResponse> lister(String nom) {
        List<Avocat> avocats = StringUtils.hasText(nom)
                ? avocatRepository.findByNomContainingIgnoreCase(nom)
                : avocatRepository.findAll();

        Map<Long, Long> charges = dossierAvocatRepository.compterDossiersActifsParAvocat(STATUTS_FERMES).stream()
                .collect(java.util.stream.Collectors.toMap(DossierAvocatRepository.ChargeAvocat::getAvocatId,
                        DossierAvocatRepository.ChargeAvocat::getTotal));
        Map<Long, DelibereRepository.PerformanceAvocat> performances = delibereRepository.calculerPerformanceParAvocat().stream()
                .collect(java.util.stream.Collectors.toMap(DelibereRepository.PerformanceAvocat::getAvocatId, Function.identity()));

        return avocats.stream()
                .map(avocat -> toResponse(avocat, charges, performances))
                .sorted(Comparator.comparingLong(AvocatRepertoireResponse::indicateurCharge))
                .toList();
    }

    private AvocatRepertoireResponse toResponse(Avocat avocat, Map<Long, Long> charges,
                                                  Map<Long, DelibereRepository.PerformanceAvocat> performances) {
        long charge = charges.getOrDefault(avocat.getId(), 0L);
        DelibereRepository.PerformanceAvocat performance = performances.get(avocat.getId());
        double taux = performance == null || performance.getTotal() == 0
                ? 0d
                : (double) performance.getFavorables() / performance.getTotal();
        return new AvocatRepertoireResponse(avocat.getId(), avocat.getNom(), avocat.getEmail(), avocat.getTelephone(), charge, taux);
    }
}
