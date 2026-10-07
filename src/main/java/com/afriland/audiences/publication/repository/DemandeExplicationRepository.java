package com.afriland.audiences.publication.repository;

import com.afriland.audiences.publication.domain.DemandeExplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Les demandes d'explication, et la question qui gouverne la relance : qui en a une sans réponse ? */
public interface DemandeExplicationRepository extends JpaRepository<DemandeExplication, Long> {

    Page<DemandeExplication> findByOrderByDateDemandeDesc(Pageable pageable);

    Page<DemandeExplication> findByAvocatIdOrderByDateDemandeDesc(Long avocatId, Pageable pageable);

    Page<DemandeExplication> findByDateReponseIsNullOrderByDateDemandeDesc(Pageable pageable);

    /**
     * Les avocats qui doivent une explication.
     *
     * <p>Interrogé une fois par passage de la relance, et non une fois par audience : le
     * planificateur parcourt des centaines d'audiences, et poser la question à chacune multiplierait
     * les allers-retours pour une réponse qui ne change pas d'une ligne à l'autre.
     */
    @org.springframework.data.jpa.repository.Query(
            "select distinct d.avocat.id from DemandeExplication d where d.dateReponse is null")
    List<Long> avocatsEnAttenteDeReponse();

    boolean existsByAvocatIdAndDateReponseIsNull(Long avocatId);

    List<DemandeExplication> findByAvocatIdInAndDateReponseIsNull(Collection<Long> avocatIds);
}
