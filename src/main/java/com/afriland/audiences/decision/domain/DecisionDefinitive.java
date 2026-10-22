package com.afriland.audiences.decision.domain;

import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.domain.EtapeProcedure;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Final decision on a case file: {@link Adjudication} (US 8.1) or {@link CondamnationPecuniaire}
 * (US 8.2), mapped as a {@code JOINED} hierarchy like {@code Publication}/{@code Intervenant}.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "decision_definitive")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING, length = 30)
public abstract class DecisionDefinitive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    /** Nullable in the schema: required for {@link Adjudication} (FR-DEF-01), optional for {@link CondamnationPecuniaire}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etape_id")
    private EtapeProcedure etape;

    @NotNull
    @Column(name = "date_decision", nullable = false)
    private LocalDate dateDecision;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
