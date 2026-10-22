package com.afriland.audiences.decision.web.dto;

import java.util.List;

/**
 * Final decisions of a case file, each list newest first. Feeds the "Final decisions" tab and is the
 * only way to find the id of a monetary judgment whose payment status is being updated.
 */
public record DecisionsDossierResponse(List<AdjudicationResponse> adjudications, List<CondamnationResponse> condamnations) {
}
