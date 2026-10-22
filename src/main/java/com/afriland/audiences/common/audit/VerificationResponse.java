package com.afriland.audiences.common.audit;

/**
 * Result of a hash verification. An unknown file is a valid answer, not an error (no 404).
 *
 * @param reconnue true if the hash matches an archive produced by this application
 * @param archive  the matching archive, or {@code null}
 */
public record VerificationResponse(boolean reconnue, ExportAuditResponse archive) {
}
