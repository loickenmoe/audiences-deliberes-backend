package com.afriland.audiences.delibere.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Prorogation d'un délibéré (RG-DEL-06, FR-DEL-04) — nombre illimité, jamais écrasé.
 * {@code compteur} porte le rang cumulé de cette prorogation pour le délibéré concerné (1 pour
 * la première, 2 pour la deuxième, etc.), calculé côté service à partir du nombre de lignes déjà
 * enregistrées — cf. dictionnaire des données §2.3.4.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prorogation")
public class Prorogation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delibere_id", nullable = false)
    private Delibere delibere;

    @NotNull
    @Column(nullable = false)
    private LocalDate date;

    @Column
    private String motif;

    @NotNull
    @Column
    private Integer compteur;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
