package com.afriland.audiences.publication.repository;

import com.afriland.audiences.publication.domain.CompteRenduAudience;
import com.afriland.audiences.publication.domain.StatutPublication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Hearing reports filed by lawyers, read by hearing.
 *
 * <p>Distinct from the legal officer's report stored on the hearing itself: a hearing can carry
 * both, one, or none. The hearing log shows them side by side, since the gap between them shows
 * who is accountable.
 */
public interface CompteRenduAudienceRepository extends JpaRepository<CompteRenduAudience, Long> {

    /**
     * Reports for these hearings, in the requested validation state.
     *
     * <p>Loaded in batch for a whole page of hearings, never one per row: a busy day's log has
     * several dozen.
     */
    List<CompteRenduAudience> findByAudienceIdInAndStatutValidation(Collection<Long> audienceIds,
                                                                    StatutPublication statutValidation);

    /**
     * RG-INT-11: has the lawyer reported on this hearing?
     *
     * <p>A REJETE report does not count: the Assistant discarded it, the hearing has no report, and
     * the lawyer still owes one. Reminders therefore resume after a rejection, which is intended (the
     * lawyer has also been notified of the rejection).
     */
    boolean existsByAudienceIdAndStatutValidationIn(Long audienceId,
                                                    Collection<StatutPublication> statuts);
}
