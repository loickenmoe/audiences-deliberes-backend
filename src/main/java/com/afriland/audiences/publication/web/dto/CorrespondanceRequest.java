package com.afriland.audiences.publication.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CorrespondanceRequest(@NotBlank String contenu) {
}
