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
 * Le compteur annuel d'une famille de références (V36).
 *
 * <p>Cette entité n'est jamais écrite par JPA : les numéros sont servis par la fonction
 * {@code prochain_numero_reference}, seule capable de le faire atomiquement. Elle existe pour deux
 * raisons : donner au repository une entité à laquelle s'accrocher, et faire valider la table par
 * Hibernate au démarrage ({@code ddl-auto=validate}) — si la migration et le code divergeaient un
 * jour, l'application refuserait de démarrer plutôt que de servir des numéros faux.
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

    /** Dernier numéro <b>servi</b> ; le prochain est celui-ci plus un. */
    @Column(nullable = false)
    private Integer dernier;

    /** Clé composite : une ligne par famille et par année. */
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
