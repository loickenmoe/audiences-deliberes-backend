package com.afriland.audiences.publication.repository;

import com.afriland.audiences.publication.domain.CorrespondanceAvocat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CorrespondanceAvocatRepository extends JpaRepository<CorrespondanceAvocat, Long> {

    boolean existsByPublicationId(Long publicationId);
}
