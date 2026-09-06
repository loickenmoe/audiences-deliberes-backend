package com.afriland.audiences.delibere.web.dto;

import com.afriland.audiences.delibere.domain.StatutExpedition;
import jakarta.validation.constraints.NotNull;

public record ExpeditionRequest(@NotNull StatutExpedition statutExpedition) {
}
