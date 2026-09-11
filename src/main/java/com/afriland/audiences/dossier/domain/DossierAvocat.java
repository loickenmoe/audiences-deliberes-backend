package com.afriland.audiences.dossier.domain;

import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Affectation d'un avocat à un dossier (RG-DOS-03). Distincte de la constitution formelle (lettre
 * signée par le SH, module intervenant M9) — cf. DECISIONS_AND_OPEN_QUESTIONS.md Q-28 : un avocat
 * peut être affecté en interne avant l'envoi de sa lettre de constitution.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dossier_avocat")
public class DossierAvocat {

    @EmbeddedId
    private DossierAvocatId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("dossierId")
    @JoinColumn(name = "dossier_id")
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("avocatId")
    @JoinColumn(name = "avocat_id")
    private Avocat avocat;

    @CreationTimestamp
    @Column(name = "date_affectation")
    private LocalDateTime dateAffectation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affecte_par")
    private Utilisateur affectePar;
}
