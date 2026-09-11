package com.afriland.audiences.publication.repository;

import com.afriland.audiences.publication.domain.CommentairePublication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentairePublicationRepository extends JpaRepository<CommentairePublication, Long> {

    List<CommentairePublication> findByPublicationIdOrderByCreatedAtAsc(Long publicationId);
}
