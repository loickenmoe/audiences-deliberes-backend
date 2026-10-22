package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.delibere.repository.DelibereRepository;
import com.afriland.audiences.dossier.domain.StatutCycleVie;
import com.afriland.audiences.dossier.repository.DossierAvocatRepository;
import com.afriland.audiences.intervenant.web.dto.AvocatRepertoireResponse;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.repository.AvocatRepository;
import lombok.RequiredArgsConstructor;
import com.afriland.audiences.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Lawyer directory (FR-INT-08, US 5.8, RG-INT-01). */
@Service
@RequiredArgsConstructor
public class RepertoireAvocatsService {

    private static final List<StatutCycleVie> STATUTS_FERMES = List.of(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE);

    private final AvocatRepository avocatRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final DelibereRepository delibereRepository;

    /**
     * Paginated in memory. US 5.8 sorts by ascending workload, which is an aggregate joined in Java,
     * so the database cannot sort or page it. Pagination only reduces what is sent to the browser;
     * for a large directory, compute the workload in SQL and page in the database.
     */
    @Transactional(readOnly = true)
    public PageResponse<AvocatRepertoireResponse> lister(String nom, Pageable pageable) {
        List<Avocat> avocats = StringUtils.hasText(nom)
                ? avocatRepository.findByNomContainingIgnoreCase(nom)
                : avocatRepository.findAll();

        Map<Long, Long> charges = dossierAvocatRepository.compterDossiersActifsParAvocat(STATUTS_FERMES).stream()
                .collect(java.util.stream.Collectors.toMap(DossierAvocatRepository.ChargeAvocat::getAvocatId,
                        DossierAvocatRepository.ChargeAvocat::getTotal));
        Map<Long, DelibereRepository.PerformanceAvocat> performances = delibereRepository.calculerPerformanceParAvocat().stream()
                .collect(java.util.stream.Collectors.toMap(DelibereRepository.PerformanceAvocat::getAvocatId, Function.identity()));

        List<AvocatRepertoireResponse> classes = avocats.stream()
                .map(avocat -> toResponse(avocat, charges, performances))
                .sorted(Comparator.comparingLong(AvocatRepertoireResponse::indicateurCharge))
                .toList();

        if (pageable.isUnpaged()) {
            return new PageResponse<>(classes, 1, classes.size());
        }
        int debut = (int) Math.min(pageable.getOffset(), classes.size());
        int fin = Math.min(debut + pageable.getPageSize(), classes.size());
        int totalPages = (int) Math.ceil((double) classes.size() / pageable.getPageSize());
        return new PageResponse<>(classes.subList(debut, fin), totalPages, classes.size());
    }

    private AvocatRepertoireResponse toResponse(Avocat avocat, Map<Long, Long> charges,
                                                  Map<Long, DelibereRepository.PerformanceAvocat> performances) {
        long charge = charges.getOrDefault(avocat.getId(), 0L);
        DelibereRepository.PerformanceAvocat performance = performances.get(avocat.getId());
        // Both counts are returned alongside the rate: "3 out of 4" also tells how many cases the
        // rate is based on. The rate is kept in the response but is no longer displayed.
        long favorables = performance == null ? 0L : performance.getFavorables();
        long total = performance == null ? 0L : performance.getTotal();
        double taux = total == 0 ? 0d : (double) favorables / total;
        return new AvocatRepertoireResponse(avocat.getId(), avocat.getNom(), avocat.getEmail(),
                avocat.getTelephone(), charge, favorables, total, taux);
    }
}
