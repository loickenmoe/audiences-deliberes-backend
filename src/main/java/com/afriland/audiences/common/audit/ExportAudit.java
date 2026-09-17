package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Une archive d'audit produite et scellée (Q-113).
 *
 * <p><b>Pourquoi cette table existe.</b> Un fichier remis à quelqu'un est un fichier perdu, et un
 * PDF se réédite en trois minutes : remis seul, il ne prouve rien. L'archive est donc déposée dans
 * MinIO, et cette ligne consigne son <b>empreinte SHA-256</b> avec la période, l'auteur et
 * l'instant. On peut alors démontrer, des années plus tard, que le fichier produit est bien celui
 * qui a été scellé.
 *
 * <p><b>La période est enregistrée avec l'empreinte</b>, et ce n'est pas un détail : sans elle,
 * l'empreinte ne prouve que l'intégrité du fichier, jamais qu'il couvre ce qu'il prétend couvrir.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "export_audit")
public class ExportAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @CreationTimestamp
    @Column(name = "date_export", updatable = false)
    private LocalDateTime dateExport;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perimetre perimetre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FormatExportAudit format;

    /** Nombre de lignes réellement écrites : ce que l'archive contient, et non ce qu'on a demandé. */
    @Column(nullable = false)
    private long lignes;

    /** SHA-256 hexadécimal des octets déposés. C'est lui qui rend l'archive vérifiable. */
    @Column(nullable = false, length = 64)
    private String empreinte;

    @Column(name = "taille_octets", nullable = false)
    private long tailleOctets;

    @Column(name = "cle_stockage", nullable = false)
    private String cleStockage;

    /**
     * Référence lisible, de la forme {@code AUD-2026-0042} (Q-114).
     *
     * <p><b>L'empreinte prouve, la référence désigne.</b> On ne dicte pas soixante-quatre caractères
     * hexadécimaux au téléphone et on ne les écrit pas dans un courrier de transmission ; c'est ce
     * que fait la référence, et c'est par elle qu'on retrouve l'empreinte.
     *
     * <p>Le numéro vient de l'identifiant et non d'un compteur annuel : un compteur remis à zéro
     * chaque année exigerait un verrou, pour un gain nul — ce qu'on attend d'une référence, c'est
     * qu'elle soit unique et qu'elle se lise.
     */
    @Column(nullable = false, length = 20)
    private String reference;
}
