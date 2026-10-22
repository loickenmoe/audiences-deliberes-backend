package com.afriland.audiences.dossier.domain;

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

/** Assignment of an in-house lawyer to a case (RG-DOS-03). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dossier_juriste")
public class DossierJuriste {

    @EmbeddedId
    private DossierJuristeId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("dossierId")
    @JoinColumn(name = "dossier_id")
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("utilisateurId")
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @CreationTimestamp
    @Column(name = "date_affectation")
    private LocalDateTime dateAffectation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affecte_par")
    private Utilisateur affectePar;
}
