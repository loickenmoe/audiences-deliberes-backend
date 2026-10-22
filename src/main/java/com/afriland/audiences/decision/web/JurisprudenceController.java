package com.afriland.audiences.decision.web;

import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.decision.domain.TypeArchiveJurisprudence;
import com.afriland.audiences.decision.service.JurisprudenceService;
import com.afriland.audiences.decision.web.dto.JurisprudenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Case-law base (FR-DEF-03, US 8.3). {@code GET /jurisprudences} is not in the formal contract; it is
 * derived from UC-DEF-03 ("searchable in the base").
 */
@RestController
@RequestMapping("${api.prefix}/jurisprudences")
@RequiredArgsConstructor
public class JurisprudenceController {

    private final JurisprudenceService jurisprudenceService;

    @PostMapping
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<JurisprudenceResponse> creer(@RequestParam(required = false) Long dossierId,
                                                          @RequestParam TypeArchiveJurisprudence typeArchive,
                                                          @RequestParam List<String> motsCles,
                                                          @RequestParam String natureDecision,
                                                          @RequestParam String juridiction,
                                                          @RequestParam LocalDate dateDecision,
                                                          @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jurisprudenceService.creer(dossierId, typeArchive, motsCles, natureDecision, juridiction, dateDecision, file));
    }

    @GetMapping
    @PreAuthorize("hasRole('CONSULTATION')")
    public PageResponse<JurisprudenceResponse> rechercher(@RequestParam(required = false) String motCle,
                                                            @RequestParam(required = false) String natureDecision,
                                                            @RequestParam(required = false) String juridiction,
                                                            @RequestParam(required = false) Long dossierId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return jurisprudenceService.rechercher(motCle, natureDecision, juridiction, dossierId, PageRequest.of(page, size));
    }
}
