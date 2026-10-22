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
 * Extension of a deliberation (RG-DEL-06, FR-DEL-04): unlimited in number, never overwritten.
 * {@code compteur} is the cumulative rank of this extension for its deliberation (1 for the first,
 * 2 for the second, and so on), computed by the service from the rows already stored.
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
