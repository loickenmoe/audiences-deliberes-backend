package com.afriland.audiences.dossier.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * New assignment of a case (FR-DOS-02, US 1.2). The endpoint <b>replaces</b> both lists (not a
 * delta).
 * <p>
 * {@code @NotEmpty}: an empty list would remove all in-house lawyers or lawyers from the case,
 * against RG-DOS-03; creation already requires both lists.
 */
public record ModifierAffectationRequest(
        @NotEmpty List<Long> juristesAffectes,
        @NotEmpty List<Long> avocatsAffectes) {
}
