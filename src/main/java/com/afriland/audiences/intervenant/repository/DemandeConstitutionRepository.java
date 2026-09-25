package com.afriland.audiences.intervenant.repository;

import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeConstitutionRepository extends JpaRepository<DemandeConstitution, Long> {

    /** M15 (Q-68) : file de travail « constitutions à signer » du SH, filtrable par statut. */
    Page<DemandeConstitution> findByStatutOrderByDateDemandeAsc(StatutConstitution statut, Pageable pageable);

    Page<DemandeConstitution> findByOrderByDateDemandeDesc(Pageable pageable);

    /** Portail avocat (Q-96) : les lettres de constitution le désignant comme prestataire. */
    List<DemandeConstitution> findByPrestataireIdOrderByDateDemandeDesc(Long prestataireId);

    /**
     * La demande dont la lettre signée est ce document de la GED. C'est le chemin qu'emprunte le
     * tirage sur papier préimprimé : l'onglet « Documents » d'un dossier ne connaît que la pièce,
     * jamais la demande qui l'a produite.
     */
    Optional<DemandeConstitution> findByLettreDocumentId(Long documentId);
}
