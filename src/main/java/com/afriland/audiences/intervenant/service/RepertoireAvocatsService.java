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

/** FR-INT-08/US 5.8 (RG-INT-01) — cf. FUNCTIONAL_MAP.md §5, API_IMPLEMENTATION_STATUS.md §7. */
@Service
@RequiredArgsConstructor
public class RepertoireAvocatsService {

    private static final List<StatutCycleVie> STATUTS_FERMES = List.of(StatutCycleVie.CLOTURE, StatutCycleVie.ARCHIVE);

    private final AvocatRepository avocatRepository;
    private final DossierAvocatRepository dossierAvocatRepository;
    private final DelibereRepository delibereRepository;

    /**
     * Paginé depuis Q-100, mais **la découpe reste en mémoire**, et il faut le dire.
     *
     * <p>US 5.8 classe le répertoire par charge croissante. Or la charge n'est pas une colonne : elle
     * vient d'un comptage agrégé, joint en Java. Aucun {@code ORDER BY} SQL ne peut donc produire cet
     * ordre, et la base ne peut pas découper ce qu'elle ne sait pas trier. La pagination réduit ici ce
     * qui **voyage** jusqu'au navigateur, pas ce que la base lit. C'est un gain réel — dix lignes au
     * lieu de tout le répertoire — mais partiel, et le jour où le répertoire deviendrait volumineux,
     * la vraie réponse serait de calculer la charge en SQL pour trier et paginer côté base.
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
        double taux = performance == null || performance.getTotal() == 0
                ? 0d
                : (double) performance.getFavorables() / performance.getTotal();
        return new AvocatRepertoireResponse(avocat.getId(), avocat.getNom(), avocat.getEmail(), avocat.getTelephone(), charge, taux);
    }
}
