package com.afriland.audiences.publication.repository;

import com.afriland.audiences.publication.domain.DemandeExplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Requests for explanation, and the question that drives reminders: who has one unanswered? */
public interface DemandeExplicationRepository extends JpaRepository<DemandeExplication, Long> {

    Page<DemandeExplication> findByOrderByDateDemandeDesc(Pageable pageable);

    Page<DemandeExplication> findByAvocatIdOrderByDateDemandeDesc(Long avocatId, Pageable pageable);

    Page<DemandeExplication> findByDateReponseIsNullOrderByDateDemandeDesc(Pageable pageable);

    /**
     * Lawyers who owe an explanation.
     *
     * <p>Queried once per reminder run, not once per hearing: the scheduler goes through hundreds of
     * hearings and the answer does not change from one row to the next.
     */
    @org.springframework.data.jpa.repository.Query(
            "select distinct d.avocat.id from DemandeExplication d where d.dateReponse is null")
    List<Long> avocatsEnAttenteDeReponse();

    boolean existsByAvocatIdAndDateReponseIsNull(Long avocatId);

    List<DemandeExplication> findByAvocatIdInAndDateReponseIsNull(Collection<Long> avocatIds);
}
