package com.afriland.audiences.common.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Yearly counter of a reference family (migration V36).
 *
 * <p>Never written through JPA: numbers are served atomically by the database function
 * {@code prochain_numero_reference}. The entity gives the repository a type and lets Hibernate
 * validate the table at startup ({@code ddl-auto=validate}), so a schema drift prevents startup.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(CompteurReference.Cle.class)
@Table(name = "compteur_reference")
public class CompteurReference {

    @Id
    @Column(nullable = false, length = 20)
    private String famille;

    @Id
    @Column(nullable = false)
    private Integer annee;

    /** Last number served; the next one is this value plus one. */
    @Column(nullable = false)
    private Integer dernier;

    /** Composite key: one row per family and year. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cle implements Serializable {
        private String famille;
        private Integer annee;

        @Override
        public boolean equals(Object autre) {
            if (this == autre) {
                return true;
            }
            if (!(autre instanceof Cle cle)) {
                return false;
            }
            return java.util.Objects.equals(famille, cle.famille) && java.util.Objects.equals(annee, cle.annee);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(famille, annee);
        }
    }
}
