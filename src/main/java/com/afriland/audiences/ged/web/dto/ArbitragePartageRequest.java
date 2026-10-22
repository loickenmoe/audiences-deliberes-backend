package com.afriland.audiences.ged.web.dto;

import com.afriland.audiences.ged.domain.DecisionPartage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DJ or DJA decision on a proposed share. The service requires a reason on rejection: the legal
 * officer who proposed must know why the document is not shared.
 */
public record ArbitragePartageRequest(@NotNull DecisionPartage decision, @Size(max = 1000) String motif) {
}
