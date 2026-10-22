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
 * Assignment of a lawyer to a case (RG-DOS-03). Distinct from the formal appointment (letter signed
 * by the SH, stakeholder module): a lawyer can be assigned internally before the appointment letter
 * is sent.
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
