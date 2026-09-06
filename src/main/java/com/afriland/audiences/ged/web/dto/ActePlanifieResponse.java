package com.afriland.audiences.ged.web.dto;

import java.time.LocalDate;

public record ActePlanifieResponse(Long dossierId, String reference, LocalDate datePlanifiee) {
}
