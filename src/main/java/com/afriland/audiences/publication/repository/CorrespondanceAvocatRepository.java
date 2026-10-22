package com.afriland.audiences.publication.repository;

import java.util.List;

import com.afriland.audiences.publication.domain.CorrespondanceAvocat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CorrespondanceAvocatRepository extends JpaRepository<CorrespondanceAvocat, Long> {

    boolean existsByPublicationId(Long publicationId);

    /** The correspondence already sent, shown on the publication detail. */
    Optional<CorrespondanceAvocat> findByPublicationId(Long publicationId);

    /**
     * Correspondences for a page of publications, in a single query.
     *
     * <p>The lawyer portal shows ten per page: one read per publication would issue ten queries where
     * one suffices, on the screen the lawyer opens first.
     */
    List<CorrespondanceAvocat> findByPublicationIdIn(java.util.Collection<Long> publicationIds);
}
