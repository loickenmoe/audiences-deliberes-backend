package com.afriland.audiences.referentiel.web.dto;

import java.time.LocalDateTime;

/**
 * State of the current user's stored signature.
 *
 * <p>Does <b>not</b> carry the image: the UI only needs to know whether to offer an upload or a
 * replacement. The image has its own endpoint, so it is not sent on every load.
 */
public record SignatureResponse(boolean deposee, String type, LocalDateTime deposeeLe) {
}
