package com.afriland.audiences.publication.repository;

import com.afriland.audiences.publication.domain.CorrespondanceAvocat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CorrespondanceAvocatRepository extends JpaRepository<CorrespondanceAvocat, Long> {

    boolean existsByPublicationId(Long publicationId);

    /** Q-86 : la correspondance déjà transmise, affichée sur le détail de la publication. */
    Optional<CorrespondanceAvocat> findByPublicationId(Long publicationId);
}
