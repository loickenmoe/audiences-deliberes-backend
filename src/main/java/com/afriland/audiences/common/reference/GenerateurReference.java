package com.afriland.audiences.common.reference;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Assigns system-generated references such as {@code DOS-2026-0001} or {@code CLI-2026-0001}:
 * prefix, year and four-digit number, the same format as audit archives ({@code AUD-2026-0042}).
 *
 * <p>Unlike audit archives, the number restarts at 1 each year, so {@code DOS-2027-0158} reads as
 * "the 158th case of 2027"; atomicity comes from the database (V36).
 *
 * <p>A served number is never given back: if creation fails afterwards, the sequence has a gap.
 * Reusing numbers would make a reference quoted in a letter later designate another case.
 */
@Service
@RequiredArgsConstructor
public class GenerateurReference {

    private final CompteurReferenceRepository compteurReferenceRepository;

    /**
     * Next reference of this family for the current year.
     *
     * <p>{@code REQUIRES_NEW} is essential: inside the creation transaction, a later failure would
     * roll back the increment and two concurrent creations could get the same reference.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String prochaine(FamilleReference famille) {
        int annee = LocalDate.now().getYear();
        int numero = compteurReferenceRepository.prochainNumero(famille.name(), annee);
        return formater(famille, annee, numero);
    }

    /**
     * Formatting, isolated to be testable without a database. Beyond 9999 in a year the number
     * grows to five digits rather than wrapping, so a reference never designates two objects.
     */
    static String formater(FamilleReference famille, int annee, int numero) {
        return String.format("%s-%d-%04d", famille.prefixe(), annee, numero);
    }
}
